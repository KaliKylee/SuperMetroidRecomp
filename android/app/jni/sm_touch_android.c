/*
 * Gamepad virtual para os controles de toque do Android.
 *
 * A UI Java (TouchOverlay) chama nativeSetButton() com o índice do botão do
 * SDL_GameController. Aqui expomos um joystick virtual do SDL2 do tipo
 * "game controller", então o caminho de gamepad do framework (GamepadMap) o
 * trata como qualquer controle físico, sem tocar no snesrecomp.
 */
#ifdef __ANDROID__
#include <jni.h>
#include <stdio.h>
#include <SDL.h>

static SDL_Joystick *g_joy = NULL;

static int AttachVirtual(void) {
  SDL_VirtualJoystickDesc d;
  SDL_zero(d);
  d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
  d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
  d.naxes = SDL_CONTROLLER_AXIS_MAX;
  d.nbuttons = SDL_CONTROLLER_BUTTON_MAX;
  d.vendor_id = 0x1209;
  d.product_id = 0x5EED;
  d.name = "Touch Controls";

  int idx = SDL_JoystickAttachVirtualEx(&d);
  if (idx < 0) return 0;

  /* Se o SDL não gerou um mapeamento padrão, registra um explícito. */
  if (!SDL_IsGameController(idx)) {
    char guid[33], map[512];
    SDL_JoystickGetGUIDString(SDL_JoystickGetDeviceGUID(idx), guid, sizeof guid);
    snprintf(map, sizeof map,
             "%s,Touch Controls,a:b0,b:b1,x:b2,y:b3,back:b4,start:b6,"
             "leftshoulder:b9,rightshoulder:b10,dpup:b11,dpdown:b12,"
             "dpleft:b13,dpright:b14,leftx:a0,lefty:a1,rightx:a2,righty:a3,"
             "lefttrigger:a4,righttrigger:a5,platform:Android",
             guid);
    SDL_GameControllerAddMapping(map);
  }
  g_joy = SDL_JoystickOpen(idx);
  return g_joy != NULL;
}

JNIEXPORT jboolean JNICALL
Java_com_snesrecomp_supermetroid_TouchOverlay_nativeTryAttach(JNIEnv *env, jclass cls) {
  if (g_joy) return JNI_TRUE;
  /* Só liga depois que o framework iniciou o subsistema de joystick. */
  if (!SDL_WasInit(SDL_INIT_JOYSTICK)) return JNI_FALSE;
  return AttachVirtual() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_snesrecomp_supermetroid_TouchOverlay_nativeSetButton(JNIEnv *env, jclass cls,
                                                               jint button, jboolean down) {
  if (!g_joy || button < 0 || button >= SDL_CONTROLLER_BUTTON_MAX) return;
  SDL_JoystickSetVirtualButton(g_joy, button, down ? 1 : 0);
}
#endif /* __ANDROID__ */
