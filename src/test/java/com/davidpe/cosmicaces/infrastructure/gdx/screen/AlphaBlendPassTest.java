package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.GL20;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlphaBlendPassTest {

  private static final int PREVIOUS_SOURCE_RGB = GL20.GL_ONE;
  private static final int PREVIOUS_DESTINATION_RGB = GL20.GL_ONE;
  private static final int PREVIOUS_SOURCE_ALPHA = GL20.GL_ZERO;
  private static final int PREVIOUS_DESTINATION_ALPHA = GL20.GL_ONE_MINUS_DST_ALPHA;

  @Test
  void enablesAlphaBlendingBeforeThePassAndRestoresSeparateFactorsAfterFlushing() {
    RecordingGl gl = new RecordingGl(false, PREVIOUS_SOURCE_RGB, PREVIOUS_DESTINATION_RGB,
        PREVIOUS_SOURCE_ALPHA, PREVIOUS_DESTINATION_ALPHA);
    AlphaBlendPass pass = new AlphaBlendPass();

    pass.run(gl.gl(), () -> {
      assertTrue(gl.blendEnabled(), "blending must be enabled while the particle pass runs");
      assertFactors(gl, AlphaBlendPass.SOURCE_RGB, AlphaBlendPass.DESTINATION_RGB,
          AlphaBlendPass.SOURCE_ALPHA, AlphaBlendPass.DESTINATION_ALPHA);
      gl.record("emit-streaks");
      gl.record("flush-vertices");
      assertTrue(gl.blendEnabled(),
          "vertices must be flushed while blending is still enabled, before restoration");
    });

    int enable = gl.indexOf("enable:" + GL20.GL_BLEND);
    int factors = gl.indexOf("blendFuncSeparate:" + AlphaBlendPass.SOURCE_RGB + ","
        + AlphaBlendPass.DESTINATION_RGB + "," + AlphaBlendPass.SOURCE_ALPHA + ","
        + AlphaBlendPass.DESTINATION_ALPHA);
    int emit = gl.indexOf("emit-streaks");
    int flush = gl.indexOf("flush-vertices");
    int restoreFactors = gl.indexOf("blendFuncSeparate:" + PREVIOUS_SOURCE_RGB + ","
        + PREVIOUS_DESTINATION_RGB + "," + PREVIOUS_SOURCE_ALPHA + ","
        + PREVIOUS_DESTINATION_ALPHA);
    int disable = gl.indexOf("disable:" + GL20.GL_BLEND);
    assertTrue(enable >= 0 && factors > enable, "blend must be enabled with the alpha factors first");
    assertTrue(emit > factors && flush > emit, "streaks must be emitted after enabling blend");
    assertTrue(restoreFactors > flush && disable > restoreFactors,
        "separate RGB/alpha factors and the enable flag must be restored only after the flush");
    assertFalse(gl.blendEnabled(), "a previously disabled blend state must stay disabled");
    assertFactors(gl, PREVIOUS_SOURCE_RGB, PREVIOUS_DESTINATION_RGB, PREVIOUS_SOURCE_ALPHA,
        PREVIOUS_DESTINATION_ALPHA);
  }

  @Test
  void preservesAnAlreadyEnabledBlendStateAndItsIndependentFactors() {
    RecordingGl gl = new RecordingGl(true, GL20.GL_SRC_COLOR, GL20.GL_ONE_MINUS_SRC_COLOR,
        GL20.GL_ONE_MINUS_DST_ALPHA, GL20.GL_DST_ALPHA);
    AlphaBlendPass pass = new AlphaBlendPass();

    pass.run(gl.gl(), () -> assertTrue(gl.blendEnabled()));

    assertTrue(gl.blendEnabled(), "a previously enabled blend state must remain enabled");
    assertFactors(gl, GL20.GL_SRC_COLOR, GL20.GL_ONE_MINUS_SRC_COLOR,
        GL20.GL_ONE_MINUS_DST_ALPHA, GL20.GL_DST_ALPHA);
    assertEquals(-1, gl.indexOf("disable:" + GL20.GL_BLEND),
        "the pass must not disable blending that was already enabled");
  }

  @Test
  void restoresBlendStateWhenThePassThrows() {
    RecordingGl gl = new RecordingGl(false, PREVIOUS_SOURCE_RGB, PREVIOUS_DESTINATION_RGB,
        PREVIOUS_SOURCE_ALPHA, PREVIOUS_DESTINATION_ALPHA);
    AlphaBlendPass pass = new AlphaBlendPass();

    assertThrows(IllegalStateException.class, () -> pass.run(gl.gl(), () -> {
      gl.record("emit-streaks");
      throw new IllegalStateException("draw failure");
    }));

    assertFalse(gl.blendEnabled(), "the previous disabled state must survive a failing pass");
    assertFactors(gl, PREVIOUS_SOURCE_RGB, PREVIOUS_DESTINATION_RGB, PREVIOUS_SOURCE_ALPHA,
        PREVIOUS_DESTINATION_ALPHA);
    assertTrue(gl.indexOf("disable:" + GL20.GL_BLEND) > gl.indexOf("emit-streaks"),
        "restoration must still run after the drawing failure");
  }

  @Test
  void rejectsMissingGlOrPass() {
    AlphaBlendPass pass = new AlphaBlendPass();
    assertThrows(IllegalArgumentException.class, () -> pass.run(null, () -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> pass.run(new RecordingGl(false, 0, 0, 0, 0).gl(), null));
  }

  private static void assertFactors(RecordingGl gl, int sourceRgb, int destinationRgb,
      int sourceAlpha, int destinationAlpha) {
    assertEquals(sourceRgb, gl.sourceRgb(), "RGB source factor");
    assertEquals(destinationRgb, gl.destinationRgb(), "RGB destination factor");
    assertEquals(sourceAlpha, gl.sourceAlpha(), "alpha source factor");
    assertEquals(destinationAlpha, gl.destinationAlpha(), "alpha destination factor");
  }

  /** Minimal GL20 proxy that records only the blend calls the pass may issue. */
  private static final class RecordingGl implements InvocationHandler {

    private final List<String> calls = new ArrayList<>();
    private boolean blendEnabled;
    private int sourceRgb;
    private int destinationRgb;
    private int sourceAlpha;
    private int destinationAlpha;
    private GL20 proxy;

    private RecordingGl(boolean blendEnabled, int sourceRgb, int destinationRgb, int sourceAlpha,
        int destinationAlpha) {
      this.blendEnabled = blendEnabled;
      this.sourceRgb = sourceRgb;
      this.destinationRgb = destinationRgb;
      this.sourceAlpha = sourceAlpha;
      this.destinationAlpha = destinationAlpha;
    }

    GL20 gl() {
      if (proxy == null) {
        proxy = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] {GL20.class},
            this);
      }
      return proxy;
    }

    boolean blendEnabled() {
      return blendEnabled;
    }

    int sourceRgb() {
      return sourceRgb;
    }

    int destinationRgb() {
      return destinationRgb;
    }

    int sourceAlpha() {
      return sourceAlpha;
    }

    int destinationAlpha() {
      return destinationAlpha;
    }

    void record(String call) {
      calls.add(call);
    }

    int indexOf(String call) {
      return calls.indexOf(call);
    }

    @Override
    public Object invoke(Object ignored, Method method, Object[] arguments) {
      switch (method.getName()) {
        case "glIsEnabled":
          calls.add("isEnabled:" + arguments[0]);
          return blendEnabled;
        case "glEnable":
          calls.add("enable:" + arguments[0]);
          if ((int) arguments[0] == GL20.GL_BLEND) {
            blendEnabled = true;
          }
          return null;
        case "glDisable":
          calls.add("disable:" + arguments[0]);
          if ((int) arguments[0] == GL20.GL_BLEND) {
            blendEnabled = false;
          }
          return null;
        case "glBlendFuncSeparate":
          sourceRgb = (int) arguments[0];
          destinationRgb = (int) arguments[1];
          sourceAlpha = (int) arguments[2];
          destinationAlpha = (int) arguments[3];
          calls.add("blendFuncSeparate:" + sourceRgb + "," + destinationRgb + "," + sourceAlpha + ","
              + destinationAlpha);
          return null;
        case "glGetIntegerv":
          int parameter = (int) arguments[0];
          ((IntBuffer) arguments[1]).put(0, factorFor(parameter));
          calls.add("getIntegerv:" + parameter);
          return null;
        default:
          return defaultValue(method.getReturnType());
      }
    }

    private int factorFor(int parameter) {
      return switch (parameter) {
        case GL20.GL_BLEND_SRC_RGB -> sourceRgb;
        case GL20.GL_BLEND_DST_RGB -> destinationRgb;
        case GL20.GL_BLEND_SRC_ALPHA -> sourceAlpha;
        case GL20.GL_BLEND_DST_ALPHA -> destinationAlpha;
        default -> throw new AssertionError("unexpected GL parameter " + parameter);
      };
    }

    private static Object defaultValue(Class<?> returnType) {
      if (returnType == boolean.class) {
        return false;
      }
      if (returnType == int.class) {
        return 0;
      }
      if (returnType == float.class) {
        return 0f;
      }
      if (returnType == long.class) {
        return 0L;
      }
      if (returnType == double.class) {
        return 0d;
      }
      if (returnType == short.class) {
        return (short) 0;
      }
      if (returnType == byte.class) {
        return (byte) 0;
      }
      if (returnType == char.class) {
        return (char) 0;
      }
      return null;
    }
  }
}
