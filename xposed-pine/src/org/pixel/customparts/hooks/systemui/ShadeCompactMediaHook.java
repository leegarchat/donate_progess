package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewParent;
import android.view.View.OnAttachStateChangeListener;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import org.pixel.customparts.core.BaseHook;

/**
 * Xposed/Pine implementation of EvolutionX compact QS media player mode.
 *
 * Native logic lives in SystemUI's MediaViewController and (in non-scene mode) is essentially:
 * <pre>
 * if (isCompactMode) collapsedLayout else expandedLayout
 * </pre>
 *
 * This hook forces the same "use collapsed constraints" behavior when the Pine setting key
 * {@code qs_compact_player} is enabled.
 */
public class ShadeCompactMediaHook extends BaseHook {

	private static final String KEY_QS_COMPACT_PLAYER = "qs_compact_player";
	private static final String KEY_NATIVE_COMPACT_MODE = "qs_compact_media_player_mode";

	private static final String KEY_HIDE_EXPAND = "qs_player_hide_expand";
	private static final String KEY_HIDE_NOTIFY = "qs_player_hide_notify";
	private static final String KEY_HIDE_LOCKSCREEN = "qs_player_hide_lockscreen";
	private static final String KEY_PLAYER_ALPHA = "qs_player_alpha";

	private static final String MEDIA_HOST_STATES_MANAGER_CLASS =
			"com.android.systemui.media.controls.ui.controller.MediaHostStatesManager";
	private static final String MEASUREMENT_OUTPUT_CLASS =
			"com.android.systemui.util.animation.MeasurementOutput";

	private static volatile Context sAppContext;

	private static final String XML_EXPANDED = "media_session_expanded";
	private static final String XML_COLLAPSED = "media_session_collapsed";

	private static final String[] ICON_ID_CANDIDATES = new String[] {
			"icon",
			"app_icon",
	};

	private static final String[] OUTPUT_ID_CANDIDATES = new String[] {
			"media_seamless",
			"seamless",
			"seamless_button",
			"output_switcher",
	};

	private static final String[] BACKGROUND_ID_CANDIDATES = new String[] {
			"album_art",
			"turbulence_noise_view",
			"loading_effect_view",
			"touch_ripple_view",
			"media_scrim",
			"notification_material_background_dimmed",
			"background",
	};

	// Fine-tune: after collapsing header, remaining content was slightly too high.
	// Reduce the upward shift by this many pixels to match native-looking centering.
	private static final float VERY_COMPACT_SHIFT_ADJUST_PX = -20f;

	// When SystemUI loads constraint sets, we pair the expanded ConstraintSet instance with
	// the collapsed instance, then later swap expanded->collapsed at calculateViewState time.
	// MUST be wrapped in synchronizedMap: ConstraintSet.load() can be called from media worker
	// threads (MediaDataManager) concurrently with calculateViewState on the Choreographer thread.
	// Concurrent WeakHashMap access causes infinite loops during rehash -> Watchdog kills SystemUI.
	private static final Map<Object, Object> sExpandedToCollapsed =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final ThreadLocal<Object> sLastCollapsedLoaded = new ThreadLocal<>();
	private static final ThreadLocal<Long> sLastCollapsedLoadedAtMs = new ThreadLocal<>();

	private static volatile boolean sLoggedConstraintSetPair = false;
	private static volatile boolean sLoggedCalculateSwap = false;

	private static final String MEDIA_VIEW_CONTROLLER_CLASS =
			"com.android.systemui.media.controls.ui.controller.MediaViewController";
	private static final String MEDIA_HOST_CLASS =
			"com.android.systemui.media.controls.ui.view.MediaHost";
	private static final String MEDIA_HIERARCHY_MANAGER_CLASS =
			"com.android.systemui.media.controls.ui.controller.MediaHierarchyManager";
	private static final String TRANSITION_LAYOUT_CONTROLLER_CLASS =
			"com.android.systemui.util.animation.TransitionLayoutController";

	private static final ThreadLocal<TransitionInfo> sTransitionInfo = new ThreadLocal<>();

	private static volatile boolean sLoggedLocationConstants = false;

	private static final class TransitionInfo {
		final Context context;
		final int startLocation;
		final int endLocation;
		final float progress;

		TransitionInfo(Context context, int startLocation, int endLocation, float progress) {
			this.context = context;
			this.startLocation = startLocation;
			this.endLocation = endLocation;
			this.progress = progress;
		}
	}

	private static final class Locations {
		Integer qs;
		Integer qqs;
		Integer lockscreen;
	}

	private static final Locations sLocations = new Locations();

	private enum Area {
		UNKNOWN,
		QS,
		SHADE,
		LOCKSCREEN,
	}

	private static final ConcurrentHashMap<Integer, Area> sLocationToArea = new ConcurrentHashMap<>();
	private static volatile boolean sLoggedLearnedAreas = false;
	private static volatile boolean sLoggedAreaLearnDebug = false;
	private static final WeakHashMap<View, Boolean> sHostAttachListenerInstalled = new WeakHashMap<>();

	@Override
	public String getHookId() {
		return "ShadeCompactMediaHook";
	}

	@Override
	public int getPriority() {
		return 67;
	}

	@Override
	public boolean isEnabled(Context context) {
		if (context != null) {
			Context app = context.getApplicationContext();
			sAppContext = (app != null) ? app : context;
		}

		// Important: hide flags must work even when compact mode is OFF (mode=0).
		boolean anyHide = false;
		try {
			anyHide = isSettingEnabled(context, KEY_HIDE_EXPAND, false)
					|| isSettingEnabled(context, KEY_HIDE_NOTIFY, false)
					|| isSettingEnabled(context, KEY_HIDE_LOCKSCREEN, false);
		} catch (Throwable ignored) {
		}

		boolean alphaActive = false;
		try {
			alphaActive = Math.abs(getPlayerAlpha(context) - 1f) > 0.001f;
		} catch (Throwable ignored) {
		}

		return anyHide || alphaActive || getMode(context) != Mode.OFF;
	}

	private float getPlayerAlpha(Context context) {
		if (context == null) return 1f;
		float a = 1f;
		try {
			// Preferred: PineEnvironment (Settings.Global + _pine suffix)
			a = getFloatSetting(context, KEY_PLAYER_ALPHA, 1f);
		} catch (Throwable ignored) {
		}
		// Compatibility: if stored as 0..100, normalize.
		if (a > 1.01f) a = a / 100f;
		if (a < 0f) a = 0f;
		if (a > 1f) a = 1f;
		return a;
	}

	@SuppressWarnings("unchecked")
	private void applyPlayerBackgroundAlpha(Context context, Object transitionViewState) {
		if (context == null || transitionViewState == null) return;
		float alpha = getPlayerAlpha(context);
		if (Math.abs(alpha - 1f) < 0.001f) return;
		try {
			Object widgetStatesObj = XposedHelpers.getObjectField(transitionViewState, "widgetStates");
			if (!(widgetStatesObj instanceof Map)) return;
			Map<Object, Object> widgetStates = (Map<Object, Object>) widgetStatesObj;

			String pkg = context.getPackageName();
			if (pkg == null || pkg.isEmpty()) pkg = "com.android.systemui";

			for (String idName : BACKGROUND_ID_CANDIDATES) {
				int id = 0;
				try {
					id = context.getResources().getIdentifier(idName, "id", pkg);
				} catch (Throwable ignored) {
				}
				if (id == 0) continue;
				Object ws = widgetStates.get(id);
				if (ws == null) continue;
				try {
					XposedHelpers.setFloatField(ws, "alpha", alpha);
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable t) {
			logError("applyPlayerBackgroundAlpha failed", t);
		}
	}

	@Override
	protected void onInit(ClassLoader classLoader) {
		// Try direct controller method hooks first (works on non-obfuscated builds),
		// but always install an R8-proof fallback based on resource loading.
		hookMediaViewController(classLoader);
		hookConstraintSetLoadFallback(classLoader);
		hookTransitionLayoutCalculateViewState(classLoader);
		hookMediaViewControllerSetCurrentStateCapture(classLoader);
		hookTransitionLayoutControllerSetStateApplyHide(classLoader);
		hookMediaHostInitLearnAreas(classLoader);
		hookMediaHostStatesManagerUpdateCarouselDimensions(classLoader);
	}

	private void hookMediaHostStatesManagerUpdateCarouselDimensions(ClassLoader classLoader) {
		try {
			Class<?> mgrClass = XposedHelpers.findClass(MEDIA_HOST_STATES_MANAGER_CLASS, classLoader);
			// Method: updateCarouselDimensions(int location, MediaHostState hostState): MeasurementOutput
			Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
					mgrClass,
					"updateCarouselDimensions",
					new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) {
							try {
								if (param.args == null || param.args.length < 2) return;
								if (!(param.args[0] instanceof Integer)) return;
								int location = (Integer) param.args[0];

								Context ctx = sAppContext;
								if (ctx == null) return;

								if (!isHiddenForLocation(ctx, location)) return;

								Object result = param.getResult();
								if (result == null) return;
								// Force height to 0 so host/carousel doesn't reserve vertical space.
								// Do NOT force width to 0: that makes bounds animate toward (0,0) (top-left shrink).
								try {
									XposedHelpers.setIntField(result, "measuredHeight", 0);
								} catch (Throwable ignored) {
								}

								// Also update carouselSizes[location] cache if present.
								try {
									Object mapObj = XposedHelpers.getObjectField(param.thisObject, "carouselSizes");
									if (mapObj instanceof Map) {
										@SuppressWarnings("unchecked")
										Map<Object, Object> map = (Map<Object, Object>) mapObj;
										Object cached = map.get(location);
										if (cached != null) {
											try {
												XposedHelpers.setIntField(cached, "measuredHeight", 0);
											} catch (Throwable ignored) {
											}
										}
									}
								} catch (Throwable ignored) {
								}
							} catch (Throwable t) {
								logError("updateCarouselDimensions hide sizing failed", t);
							}
						}
					}
			);

			log("MediaHostStatesManager.updateCarouselDimensions hide hook installed (methods hooked="
					+ (unhooks == null ? 0 : unhooks.size()) + ")");
		} catch (Throwable t) {
			logError("Failed to hook MediaHostStatesManager.updateCarouselDimensions", t);
		}
	}

	private void hookMediaHostInitLearnAreas(ClassLoader classLoader) {
		try {
			Class<?> hostClass = XposedHelpers.findClass(MEDIA_HOST_CLASS, classLoader);
			// Prefer named method.
			Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
					hostClass,
					"init",
					new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) {
							try {
								if (param.args == null || param.args.length < 1) return;
								if (!(param.args[0] instanceof Integer)) return;
								int location = (Integer) param.args[0];

								Object hostViewObj = null;
								try {
									hostViewObj = XposedHelpers.getObjectField(param.thisObject, "hostView");
								} catch (Throwable ignored) {
								}
								if (!(hostViewObj instanceof View)) {
									try {
										hostViewObj = XposedHelpers.callMethod(param.thisObject, "getHostView");
									} catch (Throwable ignored) {
									}
								}
								if (!(hostViewObj instanceof View)) return;

								View hostView = (View) hostViewObj;
								Context ctx = hostView.getContext();
								if (ctx == null) return;

								Area a = inferAreaFromView(ctx, hostView);
								if (a != Area.UNKNOWN) {
									sLocationToArea.put(location, a);
									if (!sLoggedLearnedAreas) {
										sLoggedLearnedAreas = true;
										log("Learned media location areas (from MediaHost.init): " + sLocationToArea.toString());
									}
									return;
								}

								// If the host isn't attached yet, parent chain may be incomplete. Learn once attached.
								installHostAttachLearningOnce(hostView, location);
								if (!sLoggedAreaLearnDebug) {
									sLoggedAreaLearnDebug = true;
									log("MediaHost.init: area UNKNOWN now; will retry on attach. Chain="
											+ dumpParentChain(ctx, hostView));
								}
							} catch (Throwable t) {
								logError("MediaHost.init area learning failed", t);
							}
						}
					}
			);

			int sigHooks = 0;
			if (unhooks == null || unhooks.isEmpty()) {
				// Signature-based fallback: (int) -> void
				for (Method m : hostClass.getDeclaredMethods()) {
					try {
						Class<?>[] pts = m.getParameterTypes();
						if (pts == null || pts.length != 1 || pts[0] != Integer.TYPE) continue;
						if (m.getReturnType() != Void.TYPE) continue;
						m.setAccessible(true);
						XposedBridge.hookMethod(m, new XC_MethodHook() {
							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								try {
									if (param.args == null || param.args.length < 1) return;
									int location = (Integer) param.args[0];
									Object hostViewObj = null;
									try {
										hostViewObj = XposedHelpers.getObjectField(param.thisObject, "hostView");
									} catch (Throwable ignored) {
									}
									if (!(hostViewObj instanceof View)) return;
									View hostView = (View) hostViewObj;
									Context ctx = hostView.getContext();
									if (ctx == null) return;
									Area a = inferAreaFromView(ctx, hostView);
									if (a == Area.UNKNOWN) return;
									sLocationToArea.put(location, a);
								} catch (Throwable ignored) {
								}
							}
						});
						sigHooks++;
					} catch (Throwable ignored) {
					}
				}
			}

			log("MediaHost.init area learning hooks installed (nameHooks="
					+ (unhooks == null ? 0 : unhooks.size()) + ", signatureHooks=" + sigHooks + ")");
		} catch (Throwable t) {
			logError("Failed to hook MediaHost.init for area learning", t);
		}
	}

	private void installHostAttachLearningOnce(final View hostView, final int location) {
		if (hostView == null) return;
		synchronized (sHostAttachListenerInstalled) {
			Boolean installed = sHostAttachListenerInstalled.get(hostView);
			if (installed != null && installed.booleanValue()) return;
			sHostAttachListenerInstalled.put(hostView, true);
		}

		try {
			hostView.addOnAttachStateChangeListener(
					new OnAttachStateChangeListener() {
						@Override
						public void onViewAttachedToWindow(View v) {
							try {
								Context ctx = v.getContext();
								if (ctx == null) return;
								Area a = inferAreaFromView(ctx, v);
								if (a == Area.UNKNOWN) return;
								sLocationToArea.put(location, a);
								log("Learned media location area on attach: loc=" + location + " -> " + a);
							} catch (Throwable ignored) {
							}
						}

						@Override
						public void onViewDetachedFromWindow(View v) {
							// no-op
						}
					}
			);
		} catch (Throwable ignored) {
		}
	}

	private String dumpParentChain(Context ctx, View view) {
		if (ctx == null || view == null) return "<null>";
		StringBuilder sb = new StringBuilder();
		try {
			View cur = view;
			ViewParent p = view.getParent();
			int depth = 0;
			while (cur != null && depth++ < 25) {
				if (depth > 1) sb.append(" -> ");
				sb.append(cur.getClass().getSimpleName());
				int id = cur.getId();
				if (id != View.NO_ID && id != 0) {
					try {
						sb.append("#").append(ctx.getResources().getResourceEntryName(id));
					} catch (Throwable ignored) {
						sb.append("#").append(id);
					}
				}
				if (p instanceof View) {
					cur = (View) p;
					p = cur.getParent();
				} else {
					break;
				}
			}
		} catch (Throwable ignored) {
		}
		return sb.toString();
	}

	private void hookMediaViewController(ClassLoader classLoader) {
		try {
			Class<?> controllerClass = XposedHelpers.findClass(MEDIA_VIEW_CONTROLLER_CLASS, classLoader);

			// 1) Non-scene path: override constraintSetForExpansion() to always return collapsedLayout.
			Set<XC_MethodHook.Unhook> csHooks = XposedBridge.hookAllMethods(
					controllerClass,
					"constraintSetForExpansion",
					new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) {
							try {
								Context ctx = getControllerContext(param.thisObject);
								if (ctx == null) return;
								if (!isCompactEnabled(ctx)) return;

								Object collapsedLayout = getCollapsedLayout(param.thisObject);
								if (collapsedLayout != null) {
									param.setResult(collapsedLayout);
								}
							} catch (Throwable t) {
								logError("constraintSetForExpansion override failed", t);
							}
						}
					}
			);

			// Method name can be changed by some ROMs/optimizations; fall back to signature match.
			int csFallbackHooks = 0;
			if (csHooks == null || csHooks.isEmpty()) {
				try {
					for (Method m : controllerClass.getDeclaredMethods()) {
						if (m == null) continue;
						Class<?> rt = m.getReturnType();
						if (rt == null || !"androidx.constraintlayout.widget.ConstraintSet".equals(rt.getName())) {
							continue;
						}
						Class<?>[] pts = m.getParameterTypes();
						if (pts == null || pts.length != 1 || pts[0] != Float.TYPE) {
							continue;
						}

						m.setAccessible(true);
						XposedBridge.hookMethod(
								m,
								new XC_MethodHook() {
									@Override
									protected void beforeHookedMethod(MethodHookParam param) {
										try {
											Context ctx = getControllerContext(param.thisObject);
											if (ctx == null) return;
											if (!isCompactEnabled(ctx)) return;

											Object collapsedLayout = getCollapsedLayout(param.thisObject);
											if (collapsedLayout != null) {
												param.setResult(collapsedLayout);
											}
										} catch (Throwable t) {
											logError("constraintSetForExpansion(signature) override failed", t);
										}
									}
								}
						);
						csFallbackHooks++;
					}
				} catch (Throwable t) {
					logError("constraintSetForExpansion signature fallback failed", t);
				}
			}

			// 2) Scene container path: obtainSceneContainerViewState() selects expanded vs collapsed
			// based on MediaHostState.expansion > 0. For compact mode, force expansion=0 just for
			// the duration of this call.
			Set<XC_MethodHook.Unhook> sceneHooks = XposedBridge.hookAllMethods(
					controllerClass,
					"obtainSceneContainerViewState",
					new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) {
							try {
								if (param.args == null || param.args.length == 0) return;
								Object state = param.args[0];
								if (state == null) return;

								Context ctx = getControllerContext(param.thisObject);
								if (ctx == null) return;
								if (!isCompactEnabled(ctx)) return;

								try {
									float old = XposedHelpers.getFloatField(state, "expansion");
									param.setObjectExtra("_pine_old_expansion", old);
									XposedHelpers.setFloatField(state, "expansion", 0f);
								} catch (Throwable ignored) {
								}
							} catch (Throwable t) {
								logError("obtainSceneContainerViewState pre-hook failed", t);
							}
						}

						@Override
						protected void afterHookedMethod(MethodHookParam param) {
							try {
								if (param.args != null && param.args.length > 0) {
									Object state = param.args[0];
									if (state != null) {
										Object oldObj = param.getObjectExtra("_pine_old_expansion");
										if (oldObj instanceof Float) {
											try {
												XposedHelpers.setFloatField(state, "expansion", (Float) oldObj);
											} catch (Throwable ignored) {
											}
										}
									}
								}
							} catch (Throwable t) {
								logError("obtainSceneContainerViewState post-hook failed", t);
							}
						}
					}
			);

			log("MediaViewController compact hooks installed (constraintSetForExpansion="
					+ (csHooks == null ? 0 : csHooks.size())
					+ ", constraintSetForExpansionFallback=" + csFallbackHooks
					+ ", obtainSceneContainerViewState=" + (sceneHooks == null ? 0 : sceneHooks.size())
					+ ")");
		} catch (Throwable t) {
			logError("Failed to hook MediaViewController for compact mode", t);
		}
	}

	private void hookMediaViewControllerSetCurrentStateCapture(ClassLoader classLoader) {
		try {
			Class<?> controllerClass = XposedHelpers.findClass(MEDIA_VIEW_CONTROLLER_CLASS, classLoader);

			int hooked = 0;
			for (Method m : controllerClass.getDeclaredMethods()) {
				try {
					Class<?>[] pts = m.getParameterTypes();
					if (pts == null || pts.length != 5) continue;
					if (pts[0] != Integer.TYPE) continue;
					if (pts[1] != Integer.TYPE) continue;
					if (pts[2] != Float.TYPE) continue;
					if (pts[3] != Boolean.TYPE) continue;
					if (pts[4] != Boolean.TYPE) continue;
					if (m.getReturnType() != Void.TYPE) continue;

					m.setAccessible(true);
					XposedBridge.hookMethod(
							m,
							new XC_MethodHook() {
								@Override
								protected void beforeHookedMethod(MethodHookParam param) {
									try {
										Context ctx = getControllerContext(param.thisObject);
										if (ctx == null) return;

										boolean anyHide =
												isSettingEnabled(ctx, KEY_HIDE_EXPAND, false)
														|| isSettingEnabled(ctx, KEY_HIDE_NOTIFY, false)
														|| isSettingEnabled(ctx, KEY_HIDE_LOCKSCREEN, false);
										if (!anyHide) return;

										int startLoc = (Integer) param.args[0];
										int endLoc = (Integer) param.args[1];
										float prog = (Float) param.args[2];
										Context appCtx = ctx.getApplicationContext();
										sTransitionInfo.set(new TransitionInfo(appCtx != null ? appCtx : ctx, startLoc, endLoc, prog));
									} catch (Throwable ignored) {
									}
								}

								@Override
								protected void afterHookedMethod(MethodHookParam param) {
									sTransitionInfo.remove();
								}
							}
					);
					hooked++;
				} catch (Throwable ignored) {
				}
			}

			log("MediaViewController setCurrentState capture hooks installed (methods hooked=" + hooked + ")");
		} catch (Throwable t) {
			logError("Failed to hook MediaViewController setCurrentState capture", t);
		}
	}

	private void hookTransitionLayoutControllerSetStateApplyHide(ClassLoader classLoader) {
		try {
			Class<?> tlcClass = XposedHelpers.findClass(TRANSITION_LAYOUT_CONTROLLER_CLASS, classLoader);

			int hooked = 0;
			for (Method m : tlcClass.getDeclaredMethods()) {
				try {
					Class<?>[] pts = m.getParameterTypes();
					if (pts == null || pts.length != 6) continue;
					// (TransitionViewState, boolean, boolean, long, long, boolean)
					if (!"com.android.systemui.util.animation.TransitionViewState".equals(pts[0].getName())) continue;
					if (pts[1] != Boolean.TYPE) continue;
					if (pts[2] != Boolean.TYPE) continue;
					if (pts[3] != Long.TYPE) continue;
					if (pts[4] != Long.TYPE) continue;
					if (pts[5] != Boolean.TYPE) continue;
					if (m.getReturnType() != Void.TYPE) continue;

					m.setAccessible(true);
					XposedBridge.hookMethod(
							m,
							new XC_MethodHook() {
								@Override
								protected void beforeHookedMethod(MethodHookParam param) {
									try {
										TransitionInfo info = sTransitionInfo.get();
										if (info == null) return;

										Object viewState = param.args[0];
										if (viewState == null) return;

										Context ctx = info.context;
										if (ctx == null) return;

										resolveLocationsIfNeeded(classLoader);
										float factor = computeVisibilityFactor(ctx, info.startLocation, info.endLocation, info.progress);
										if (factor >= 0.999f) return;
										applyHideFactorToTransitionViewState(viewState, factor);
									} catch (Throwable t) {
										logError("TransitionLayoutController.setState hide apply failed", t);
									}
								}
							}
					);
					hooked++;
				} catch (Throwable ignored) {
				}
			}

			log("TransitionLayoutController setState hide hooks installed (methods hooked=" + hooked + ")");
		} catch (Throwable t) {
			logError("Failed to hook TransitionLayoutController.setState for hide", t);
		}
	}

	private void resolveLocationsIfNeeded(ClassLoader classLoader) {
		if (sLocations.qs != null && sLocations.qqs != null && sLocations.lockscreen != null) return;
		try {
			Class<?> mhm = XposedHelpers.findClass(MEDIA_HIERARCHY_MANAGER_CLASS, classLoader);
			sLocations.qs = getFirstStaticIntFieldOrNull(mhm,
					"LOCATION_QS",
					"LOCATION_QS_PANEL",
					"LOCATION_QS_EXPANDED"
			);
			sLocations.qqs = getFirstStaticIntFieldOrNull(mhm,
					"LOCATION_QQS",
					"LOCATION_QQS_PANEL"
			);
			sLocations.lockscreen = getFirstStaticIntFieldOrNull(mhm,
					"LOCATION_LOCKSCREEN",
					"LOCATION_LOCK_SCREEN",
					"LOCATION_KEYGUARD"
			);

			if (!sLoggedLocationConstants) {
				sLoggedLocationConstants = true;
				log("Media locations resolved: QS=" + sLocations.qs
						+ ", QQS=" + sLocations.qqs
						+ ", LOCKSCREEN=" + sLocations.lockscreen);
			}
		} catch (Throwable t) {
			if (!sLoggedLocationConstants) {
				sLoggedLocationConstants = true;
				logError("Failed to resolve MediaHierarchyManager location constants", t);
			}
		}
	}

	private Integer getStaticIntFieldOrNull(Class<?> cls, String fieldName) {
		try {
			return XposedHelpers.getStaticIntField(cls, fieldName);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private Integer getFirstStaticIntFieldOrNull(Class<?> cls, String... fieldNames) {
		if (cls == null || fieldNames == null) return null;
		for (String name : fieldNames) {
			if (name == null) continue;
			Integer v = getStaticIntFieldOrNull(cls, name);
			if (v != null) return v;
		}
		return null;
	}

	private boolean isHiddenForLocation(Context context, int location) {
		if (context == null) return false;
		// In landscape the QS panel uses a dual-pane layout where TransitionLayout text views
		// are recreated mid-rotation and may have null Layout objects. Applying hide logic in
		// this state causes a NullPointerException in applyCurrentState(). Skip hiding entirely
		// in landscape to avoid the crash.
		try {
			int orient = context.getResources().getConfiguration().orientation;
			if (orient == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return false;
		} catch (Throwable ignored) {
		}
		boolean hideExpand = false;
		boolean hideNotify = false;
		boolean hideLock = false;
		try {
			hideExpand = isSettingEnabled(context, KEY_HIDE_EXPAND, false);
		} catch (Throwable ignored) {
		}
		try {
			hideNotify = isSettingEnabled(context, KEY_HIDE_NOTIFY, false);
		} catch (Throwable ignored) {
		}
		try {
			hideLock = isSettingEnabled(context, KEY_HIDE_LOCKSCREEN, false);
		} catch (Throwable ignored) {
		}

		// 1) Primary mapping (AOSP/SystemUI MediaHierarchyManager):
		// QS = 0, QQS (notification shade host) = 1, LOCKSCREEN = 2.
		if (location == 0) return hideExpand;
		if (location == 1) return hideNotify;
		if (location == 2) return hideLock;

		// 2) If constants were reflectively resolved, use them too (in case a ROM changes numbering).
		if (sLocations.qs != null && location == sLocations.qs.intValue()) return hideExpand;
		if (sLocations.qqs != null && location == sLocations.qqs.intValue()) return hideNotify;
		if (sLocations.lockscreen != null && location == sLocations.lockscreen.intValue()) return hideLock;

		// 2) Obfuscation-safe: learned mapping from location int -> semantic area.
		Area a = sLocationToArea.get(location);
		if (a == null) return false;
		switch (a) {
			case QS:
				return hideExpand;
			case SHADE:
				return hideNotify;
			case LOCKSCREEN:
				return hideLock;
			default:
				return false;
		}
	}

	private Area inferAreaFromView(Context ctx, View view) {
		if (ctx == null || view == null) return Area.UNKNOWN;
		try {
			// Include the view itself, then walk up parents.
			View pv = view;
			ViewParent p = view.getParent();
			int depth = 0;
			while (pv != null && depth++ < 40) {
					int id = pv.getId();
					if (id != View.NO_ID && id != 0) {
						String name = null;
						try {
							name = ctx.getResources().getResourceEntryName(id);
						} catch (Throwable ignored) {
						}
						if (name != null) {
							String n = name.toLowerCase();
							if (n.contains("keyguard") || n.contains("lockscreen") || n.contains("lock_screen")) {
								return Area.LOCKSCREEN;
							}
							if (n.contains("qs") || n.contains("quick_settings") || n.contains("quicksettings")) {
								return Area.QS;
							}
							if (n.contains("shade") || n.contains("notification") || n.contains("notifications")) {
								return Area.SHADE;
							}
						}
					}
				if (p instanceof View) {
					pv = (View) p;
					p = pv.getParent();
				} else {
					break;
				}
			}
		} catch (Throwable ignored) {
		}
		return Area.UNKNOWN;
	}

	private float computeVisibilityFactor(Context context, int startLocation, int endLocation, float progress) {
		boolean startHidden = isHiddenForLocation(context, startLocation);
		boolean endHidden = isHiddenForLocation(context, endLocation);

		if (startHidden && endHidden) return 0f;
		if (!startHidden && !endHidden) return 1f;
		progress = clamp01(progress);
		if (!startHidden && endHidden) return 1f - progress;
		// startHidden && !endHidden
		return progress;
	}

	private float clamp01(float v) {
		if (v < 0f) return 0f;
		if (v > 1f) return 1f;
		return v;
	}

	@SuppressWarnings("unchecked")
	private void applyHideFactorToTransitionViewState(Object transitionViewState, float factor) {
		if (transitionViewState == null) return;
		factor = clamp01(factor);
		if (factor <= 0.001f) {
			try {
				Object widgetStatesObj = XposedHelpers.getObjectField(transitionViewState, "widgetStates");
				if (widgetStatesObj instanceof Map) {
					Map<Object, Object> widgetStates = (Map<Object, Object>) widgetStatesObj;
					for (Object ws : widgetStates.values()) {
						markGone(ws);
					}
				}
			} catch (Throwable ignored) {
			}
			try {
				XposedHelpers.setIntField(transitionViewState, "height", 0);
			} catch (Throwable ignored) {
			}
			try {
				XposedHelpers.setIntField(transitionViewState, "measureHeight", 0);
			} catch (Throwable ignored) {
			}
			return;
		}

		// Garage-door effect: keep the content solid (no scaling to a point). We only reduce
		// the container height so TransitionLayout clips the bottom part as it "rolls up".
		// Do not touch alpha/y to avoid scrim/tint artifacts.

		try {
			int h = XposedHelpers.getIntField(transitionViewState, "height");
			XposedHelpers.setIntField(transitionViewState, "height", Math.round(h * factor));
		} catch (Throwable ignored) {
		}
		try {
			int mh = XposedHelpers.getIntField(transitionViewState, "measureHeight");
			XposedHelpers.setIntField(transitionViewState, "measureHeight", Math.round(mh * factor));
		} catch (Throwable ignored) {
		}
	}

	private Context getControllerContext(Object controller) {
		if (controller == null) return null;
		try {
			Object c = XposedHelpers.getObjectField(controller, "context");
			if (c instanceof Context) return (Context) c;
		} catch (Throwable ignored) {
		}
		return null;
	}

	private boolean isCompactEnabled(Context context) {
		Mode mode = getMode(context);
		return mode == Mode.COMPACT || mode == Mode.VERY;
	}

	private boolean isHeaderOnlyEnabled(Context context) {
		return getMode(context) == Mode.HEADER_ONLY;
	}

	private boolean isVeryCompactEnabled(Context context) {
		return getMode(context) == Mode.VERY;
	}

	private enum Mode {
		OFF,
		COMPACT,
		HEADER_ONLY,
		VERY,
	}

	private Mode getMode(Context context) {
		if (context == null) return Mode.OFF;

		int modeValue = 0;

		// 1) Preferred: PineEnvironment (Settings.Global + _pine suffix) as an int mode.
		try {
			modeValue = getIntSetting(context, KEY_QS_COMPACT_PLAYER, 0);
		} catch (Throwable ignored) {
		}

		// 2) Fallbacks for direct writes without suffix.
		if (modeValue == 0) {
			try {
				modeValue = Settings.Global.getInt(context.getContentResolver(), KEY_QS_COMPACT_PLAYER, 0);
			} catch (Throwable ignored) {
			}
		}
		if (modeValue == 0) {
			try {
				modeValue = Settings.System.getIntForUser(
						context.getContentResolver(),
						KEY_QS_COMPACT_PLAYER,
						0,
						UserHandle.USER_CURRENT
				);
			} catch (Throwable ignored) {
			}
		}

		// 3) Compatibility: native EvoX boolean toggle maps to mode=1.
		if (modeValue == 0) {
			try {
				int nativeEnabled = Settings.System.getIntForUser(
						context.getContentResolver(),
						KEY_NATIVE_COMPACT_MODE,
						0,
						UserHandle.USER_CURRENT
				);
				if (nativeEnabled != 0) modeValue = 1;
			} catch (Throwable ignored) {
			}
		}

		switch (modeValue) {
			case 1:
				return Mode.COMPACT;
			case 2:
				return Mode.HEADER_ONLY;
			case 3:
				return Mode.VERY;
			default:
				return Mode.OFF;
		}
	}

	/**
	 * R8/obfuscation-safe fallback.
	 *
	 * MediaViewController loads two ConstraintSets from xml:
	 * - R.xml.media_session_collapsed
	 * - R.xml.media_session_expanded
	 *
	 * On R8 builds we can't reliably hook MediaViewController methods by name, so we capture the
	 * runtime ConstraintSet instances for collapsed/expanded and later swap expanded->collapsed at
	 * TransitionLayout.calculateViewState() time.
	 */
	private void hookConstraintSetLoadFallback(ClassLoader classLoader) {
		try {
			Class<?> constraintSetClass = XposedHelpers.findClass(
					"androidx.constraintlayout.widget.ConstraintSet",
					classLoader
			);

			Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
					constraintSetClass,
					"load",
					new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) {
							try {
								if (param.args == null || param.args.length != 2) return;
								if (!(param.args[0] instanceof Context)) return;
								if (!(param.args[1] instanceof Integer)) return;

								Context ctx = (Context) param.args[0];

								int xmlResId = (Integer) param.args[1];
								String entryName;
								try {
									entryName = ctx.getResources().getResourceEntryName(xmlResId);
								} catch (Throwable t) {
									return;
								}
								if (entryName == null) return;

								// Track which ConstraintSet instance corresponds to collapsed vs expanded.
								// This is R8-safe because it relies on resource names and the ConstraintSet instance itself.
								if (entryName.equals(XML_COLLAPSED)) {
									sLastCollapsedLoaded.set(param.thisObject);
									sLastCollapsedLoadedAtMs.set(android.os.SystemClock.uptimeMillis());
									return;
								}

								if (entryName.equals(XML_EXPANDED)) {
									Object collapsed = sLastCollapsedLoaded.get();
									Long at = sLastCollapsedLoadedAtMs.get();
									long now = android.os.SystemClock.uptimeMillis();
									if (collapsed != null && at != null && (now - at.longValue()) < 2500L) {
										sExpandedToCollapsed.put(param.thisObject, collapsed);
										if (!sLoggedConstraintSetPair) {
											sLoggedConstraintSetPair = true;
											log("ConstraintSet pair captured: expanded->collapsed (will swap at calculateViewState)");
										}
									}
								}

							} catch (Throwable t) {
								logError("ConstraintSet#load fallback failed", t);
							}
						}
					}
			);

			log("ConstraintSet#load fallback installed (methods hooked=" + (unhooks == null ? 0 : unhooks.size()) + ")");
		} catch (Throwable t) {
			logError("Failed to install ConstraintSet#load fallback", t);
		}
	}

	private void hookTransitionLayoutCalculateViewState(ClassLoader classLoader) {
		try {
			Class<?> transitionLayoutClass = XposedHelpers.findClass(
					"com.android.systemui.util.animation.TransitionLayout",
					classLoader
			);

			// Use name hook first, then signature scan (R8 can rename methods).
			Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
					transitionLayoutClass,
					"calculateViewState",
					new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) {
							try {
								if (param.args == null || param.args.length < 2) return;
								Object constraintSet = param.args[1];
								if (constraintSet == null) return;

								Context ctx = null;
								if (param.thisObject instanceof View) {
									ctx = ((View) param.thisObject).getContext();
								}
								if (ctx == null) return;
								Mode mode = getMode(ctx);
								if (mode == Mode.OFF) return;
								if (mode == Mode.HEADER_ONLY) return;

								Object mapped = sExpandedToCollapsed.get(constraintSet);
								if (mapped != null && mapped != constraintSet) {
									param.args[1] = mapped;
									if (!sLoggedCalculateSwap) {
										sLoggedCalculateSwap = true;
										log("TransitionLayout.calculateViewState: swapped expanded ConstraintSet -> collapsed");
									}
								}
							} catch (Throwable t) {
								logError("TransitionLayout.calculateViewState hook failed", t);
							}
						}

						@Override
						protected void afterHookedMethod(MethodHookParam param) {
							try {
								Object result = param.getResult();
								if (result == null) return;
								Context ctx = null;
								if (param.thisObject instanceof View) {
									ctx = ((View) param.thisObject).getContext();
								}
								if (ctx == null) return;
								Mode mode = getMode(ctx);
								if (mode == Mode.HEADER_ONLY || mode == Mode.VERY) {
									applyVeryCompactHeaderCollapse(ctx, result);
								}
								applyPlayerBackgroundAlpha(ctx, result);
							} catch (Throwable t) {
								logError("TransitionLayout.calculateViewState post-hook failed", t);
							}
						}
					}
			);

			int signatureHooks = 0;
			if (unhooks == null || unhooks.isEmpty()) {
				// Signature-based fallback: return TransitionViewState, takes (MeasurementInput, ConstraintSet, TransitionViewState)
				try {
					for (Method m : transitionLayoutClass.getDeclaredMethods()) {
						if (m == null) continue;
						Class<?>[] pts = m.getParameterTypes();
						if (pts == null || pts.length != 3) continue;
						if (!"com.android.systemui.util.animation.MeasurementInput".equals(pts[0].getName())) continue;
						if (!"androidx.constraintlayout.widget.ConstraintSet".equals(pts[1].getName())) continue;
						if (!"com.android.systemui.util.animation.TransitionViewState".equals(pts[2].getName())) continue;
						Class<?> rt = m.getReturnType();
						if (rt == null || !"com.android.systemui.util.animation.TransitionViewState".equals(rt.getName())) continue;
						m.setAccessible(true);
						XposedBridge.hookMethod(m, new XC_MethodHook() {
							@Override
							protected void beforeHookedMethod(MethodHookParam param) {
								try {
									Object constraintSet = param.args[1];
									if (constraintSet == null) return;
									Context ctx = null;
									if (param.thisObject instanceof View) {
										ctx = ((View) param.thisObject).getContext();
									}
									if (ctx == null) return;
									Mode mode = getMode(ctx);
									if (mode == Mode.OFF) return;
									if (mode == Mode.HEADER_ONLY) return;
									Object mapped = sExpandedToCollapsed.get(constraintSet);
									if (mapped != null && mapped != constraintSet) {
										param.args[1] = mapped;
										if (!sLoggedCalculateSwap) {
											sLoggedCalculateSwap = true;
											log("TransitionLayout.calculateViewState(signature): swapped expanded ConstraintSet -> collapsed");
										}
									}
								} catch (Throwable t) {
									logError("TransitionLayout.calculateViewState(signature) hook failed", t);
								}
							}

							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								try {
									Object result = param.getResult();
									if (result == null) return;
									Context ctx = null;
									if (param.thisObject instanceof View) {
										ctx = ((View) param.thisObject).getContext();
									}
									if (ctx == null) return;
									Mode mode = getMode(ctx);
									if (mode == Mode.HEADER_ONLY || mode == Mode.VERY) {
										applyVeryCompactHeaderCollapse(ctx, result);
									}
									applyPlayerBackgroundAlpha(ctx, result);
								} catch (Throwable t) {
									logError("TransitionLayout.calculateViewState(signature) post-hook failed", t);
								}
							}
						});
						signatureHooks++;
					}
				} catch (Throwable t) {
					logError("TransitionLayout.calculateViewState signature fallback failed", t);
				}
			}

			log("TransitionLayout calculateViewState hooks installed (nameHooks="
					+ (unhooks == null ? 0 : unhooks.size())
					+ ", signatureHooks=" + signatureHooks + ")");
		} catch (Throwable t) {
			logError("Failed to hook TransitionLayout.calculateViewState", t);
		}
	}

	private Object getCollapsedLayout(Object controller) {
		if (controller == null) return null;
		try {
			// Public Kotlin getter for 'var collapsedLayout'
			return XposedHelpers.callMethod(controller, "getCollapsedLayout");
		} catch (Throwable ignored) {
		}
		try {
			return XposedHelpers.getObjectField(controller, "collapsedLayout");
		} catch (Throwable ignored) {
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void applyVeryCompactHeaderCollapse(Context context, Object transitionViewState) {
		if (context == null || transitionViewState == null) return;
		try {
			Object widgetStatesObj = XposedHelpers.getObjectField(transitionViewState, "widgetStates");
			if (!(widgetStatesObj instanceof Map)) return;
			Map<Object, Object> widgetStates = (Map<Object, Object>) widgetStatesObj;

			HashSet<Integer> headerIds = new HashSet<>();
			String pkg = context.getPackageName();
			if (pkg == null || pkg.isEmpty()) {
				pkg = "com.android.systemui";
			}
			for (String idName : ICON_ID_CANDIDATES) {
				int id = context.getResources().getIdentifier(idName, "id", pkg);
				if (id != 0) headerIds.add(id);
			}
			for (String idName : OUTPUT_ID_CANDIDATES) {
				int id = context.getResources().getIdentifier(idName, "id", pkg);
				if (id != 0) headerIds.add(id);
			}
			if (headerIds.isEmpty()) return;

			float minY = Float.MAX_VALUE;
			float maxBottom = -Float.MAX_VALUE;
			boolean foundAny = false;

			// 1) Hide header elements and measure their vertical span.
			for (Integer id : headerIds) {
				Object ws = widgetStates.get(id);
				if (ws == null) continue;

				int h = 0;
				float y = 0f;
				try {
					h = XposedHelpers.getIntField(ws, "height");
					y = XposedHelpers.getFloatField(ws, "y");
				} catch (Throwable ignored) {
				}

				markGone(ws);
				if (h > 0) {
					minY = Math.min(minY, y);
					maxBottom = Math.max(maxBottom, y + h);
					foundAny = true;
				}
			}
			if (!foundAny) return;

			float headerSpan = maxBottom - minY;
			if (headerSpan <= 0f) return;
			float shiftUp = Math.max(0f, headerSpan - VERY_COMPACT_SHIFT_ADJUST_PX);
			int shrinkBy = Math.max(0, Math.round(shiftUp));
			if (shiftUp <= 0f || shrinkBy <= 0) return;

			// 2) Shift everything below the header up.
			for (Object ws : widgetStates.values()) {
				if (ws == null) continue;
				try {
					float y = XposedHelpers.getFloatField(ws, "y");
					if (y >= maxBottom - 0.5f) {
						XposedHelpers.setFloatField(ws, "y", y - shiftUp);
					}
				} catch (Throwable ignored) {
				}
			}

			// 3) Reduce overall view height and keep backgrounds consistent.
			int newHeight = -1;
			try {
				int oldHeight = XposedHelpers.getIntField(transitionViewState, "height");
				newHeight = Math.max(0, oldHeight - shrinkBy);
				XposedHelpers.setIntField(transitionViewState, "height", newHeight);
			} catch (Throwable ignored) {
			}
			try {
				int oldMeasureHeight = XposedHelpers.getIntField(transitionViewState, "measureHeight");
				int mh = Math.max(0, oldMeasureHeight - shrinkBy);
				XposedHelpers.setIntField(transitionViewState, "measureHeight", mh);
				if (newHeight < 0) newHeight = mh;
			} catch (Throwable ignored) {
			}
			if (newHeight <= 0) return;

			for (String idName : BACKGROUND_ID_CANDIDATES) {
				int id = context.getResources().getIdentifier(idName, "id", pkg);
				if (id == 0) continue;
				Object ws = widgetStates.get(id);
				if (ws == null) continue;
				try {
					XposedHelpers.setIntField(ws, "height", newHeight);
					XposedHelpers.setFloatField(ws, "y", 0f);
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable t) {
			logError("applyVeryCompactHeaderCollapse failed", t);
		}
	}

	private void markGone(Object widgetState) {
		if (widgetState == null) return;
		try {
			XposedHelpers.setBooleanField(widgetState, "gone", true);
		} catch (Throwable ignored) {
		}
		try {
			XposedHelpers.setFloatField(widgetState, "alpha", 0f);
		} catch (Throwable ignored) {
		}
		try {
			XposedHelpers.setIntField(widgetState, "height", 0);
		} catch (Throwable ignored) {
		}
		try {
			XposedHelpers.setIntField(widgetState, "width", 0);
		} catch (Throwable ignored) {
		}
	}
}
