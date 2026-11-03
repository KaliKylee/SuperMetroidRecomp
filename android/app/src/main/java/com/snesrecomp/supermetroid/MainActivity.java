package com.snesrecomp.supermetroid;

import android.os.Bundle;
import android.view.ViewGroup;

import org.libsdl.app.SDLActivity;

/** SDLActivity + overlay de controles de toque por cima da superfície do jogo. */
public class MainActivity extends SDLActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLayout.addView(new TouchOverlay(this), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Espera o SDL iniciar o subsistema de joystick e então liga o gamepad virtual.
        Thread t = new Thread(() -> {
            for (int i = 0; i < 600; i++) {
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                try {
                    if (TouchOverlay.nativeTryAttach()) return;
                } catch (UnsatisfiedLinkError e) {
                    // libmain.so ainda não carregou
                }
            }
        }, "touch-attach");
        t.setDaemon(true);
        t.start();
    }
}
