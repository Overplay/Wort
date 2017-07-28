package io.ourglass.wort.support;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Point;
import android.view.View;

/**
 * Created by mkahn on 2/13/17.
 */

public class OGAnimations {

    public enum MoveAnimation { INSTANT, SLIDE, FLASHY };

    private static void doAnim(Animator anim){
        anim.setDuration(500);
        anim.start();
    }

    public static void animateAlphaTo(View v, Float endingAlpha) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(v, "alpha", v.getAlpha(), endingAlpha);
        doAnim(anim);
    }

    public static void moveView(View v, Point destination, MoveAnimation animType){

        switch (animType){

            case SLIDE:
                v.animate().translationX(destination.x).setDuration(500);
                v.animate().translationY(destination.y).setDuration(500);
                break;

            case FLASHY:

                boolean movingX = v.getTranslationX() != destination.x;
                String rotationAxis = movingX ? "rotationY" : "rotationX";

                ObjectAnimator close = ObjectAnimator.ofFloat(v, rotationAxis, 0, 80);
                close.setDuration(150);

                ObjectAnimator slideY = ObjectAnimator.ofFloat(v, "translationY", v.getTranslationY(), destination.y);
                slideY.setDuration(350);

                ObjectAnimator slideX = ObjectAnimator.ofFloat(v, "translationX", v.getTranslationX(), destination.x);
                slideX.setDuration(350);

                ObjectAnimator open = ObjectAnimator.ofFloat(v, rotationAxis, 80, 0);
                open.setDuration(150);

                AnimatorSet animSet = new AnimatorSet();
                //animSet.setInterpolator(new BounceInterpolator());
                animSet.play(close).before(slideX);
                animSet.play(slideX).with(slideY);
                animSet.play(open).after(slideY);
                animSet.start();

                break;

            case INSTANT:
            default:
                v.setTranslationX(destination.x);
                v.setTranslationY(destination.y);
                break;
        }

    }
}
