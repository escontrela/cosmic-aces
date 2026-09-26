package com.davidpe.cosmicaces.application;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Minimal LibGDX application shell; gameplay and visuals will be added incrementally. */
public class CosmicAcesGame extends ApplicationAdapter {

  public static final float WORLD_WIDTH = 800f;
  public static final float WORLD_HEIGHT = 600f;

  private OrthographicCamera camera;
  private Viewport viewport;

  @Override
  public void create() {
    camera = new OrthographicCamera();
    viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
    camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f);
    camera.update();
  }

  @Override
  public void render() {
    ScreenUtils.clear(0f, 0f, 0f, 1f);
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height);
  }
}
