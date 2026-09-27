package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ScreenUtils;

/** Empty gameplay placeholder; gameplay systems will be added in their own approved work. */
public final class EmptyGameScreen extends ScreenAdapter {

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
  }
}
