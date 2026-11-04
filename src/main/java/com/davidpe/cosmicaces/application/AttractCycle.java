package com.davidpe.cosmicaces.application;

import java.util.Optional;

/**
 * Presentation state of the arcade attraction loop: it alternates the welcome lobby and the Hall of
 * Fame consult every {@link #INTERVAL_SECONDS} seconds of active rest.
 *
 * <p>It has no LibGDX dependency and never creates a screen, a game or an event. The composition
 * root asks screen constructors to {@link #enter(Page) enter} the page they represent and calls
 * {@link #tick(double)} once at the end of a rest frame; the returned page is materialised as the
 * next screen. Screens keep {@link #setPaused(boolean)} updated so Ajustes, the initials editor, an
 * active mission or a paused application stop the countdown.
 *
 * <p>The elapsed time is accumulated from the frame delta, never from a number of frames or a
 * sleeping thread. A single tick can rotate at most once: after a long stall the loop moves to the
 * next page and discards the excess time instead of cascading through several pages. The threshold
 * is inclusive and tolerates floating-point accumulation at 30, 60 or 144 frames per second.
 */
public final class AttractCycle {

  /** Seconds of active rest before the loop changes page. */
  public static final double INTERVAL_SECONDS = 10.0;

  /**
   * Tolerance applied to the accumulated delta so 600 frames of 1/60 s count as exactly ten seconds.
   * It is far below the smallest delta of any supported refresh rate.
   */
  private static final double THRESHOLD_EPSILON_SECONDS = 1.0e-6;

  /** The two rest pages the arcade alternates while nobody is playing. */
  public enum Page {
    WELCOME,
    HALL_OF_FAME
  }

  private Page page = Page.WELCOME;
  private double elapsedSeconds;
  private boolean paused;

  /** Returns the page currently represented by the visible rest screen. */
  public Page page() {
    return page;
  }

  /** Returns how much of the current interval has already been accumulated. */
  public double elapsedSeconds() {
    return elapsedSeconds;
  }

  public boolean paused() {
    return paused;
  }

  /**
   * Pauses or resumes the countdown. Resuming always rearms a complete interval, so opening Ajustes
   * for longer than ten seconds never leaves a partially consumed timer behind.
   */
  public void setPaused(boolean paused) {
    if (this.paused == paused) {
      return;
    }
    this.paused = paused;
    if (!paused) {
      elapsedSeconds = 0.0;
    }
  }

  public void pause() {
    setPaused(true);
  }

  public void resume() {
    setPaused(false);
  }

  /**
   * Synchronises the loop with a rest screen that has just been built and starts a complete
   * interval. Called by the welcome and Hall screens when they are created, so a terminal visit or a
   * return from a mission never inherits time from an earlier rest page.
   */
  public void enter(Page page) {
    this.page = page == null ? Page.WELCOME : page;
    elapsedSeconds = 0.0;
  }

  /** Restarts the countdown without changing the visible page. */
  public void restartInterval() {
    elapsedSeconds = 0.0;
  }

  /**
   * Advances the countdown by one frame.
   *
   * @param deltaSeconds frame delta; zero, negative and non-finite values are ignored
   * @return the new page exactly when this call rotated the loop, otherwise empty
   */
  public Optional<Page> tick(double deltaSeconds) {
    if (paused || !Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
      return Optional.empty();
    }
    elapsedSeconds += deltaSeconds;
    if (elapsedSeconds + THRESHOLD_EPSILON_SECONDS < INTERVAL_SECONDS) {
      return Optional.empty();
    }
    // The excess after a long stall is discarded so one tick can never skip several pages.
    elapsedSeconds = 0.0;
    page = page == Page.WELCOME ? Page.HALL_OF_FAME : Page.WELCOME;
    return Optional.of(page);
  }

  @Override
  public String toString() {
    return "AttractCycle{page=" + page + ", elapsed=" + elapsedSeconds
        + ", paused=" + paused + '}';
  }
}
