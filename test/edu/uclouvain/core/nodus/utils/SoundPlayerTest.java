/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus, distributed under the GNU General Public License,
 * version 3 or later. See http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Exercises resource ownership with real audio conversion and no audio device. */
class SoundPlayerTest {
  @Test
  void unavailableAudioLineClosesTheConversionStream() throws Exception {
    try (Source source = new Source(true)) {
      Player player = new Player("line");
      assertThrows(LineUnavailableException.class, () -> player.playSound(source));
      assertEquals(1, source.closed);
      assertEquals(0, player.closed);
    }
  }

  @TestFactory
  Stream<DynamicTest> incompletePlaybackClosesItsClip() {
    return Stream.of("open", "listener", "start")
        .map(
            phase ->
                dynamicTest(
                    phase,
                    () -> {
                      try (Source source = new Source(false)) {
                        Player player = new Player(phase);
                        assertThrows(Exception.class, () -> player.playSound(source));
                        assertEquals(1, player.closed);
                        assertEquals(0, source.closed, "The original stream belongs to the caller");
                      }
                    }));
  }

  @Test
  void successfulPlaybackRetainsItsClipUntilTheStopEvent() throws Exception {
    try (Source source = new Source(true)) {
      Player player = new Player("");
      player.playSound(source);
      assertEquals(1, source.closed);
      assertEquals(0, player.closed);
      player.listener.update(new LineEvent(player.clip, LineEvent.Type.STOP, 0));
      assertEquals(1, player.closed);
    }
  }

  @Test
  void failedConversionClosureAlsoClosesAnAlreadyLoadedClip() throws Exception {
    try (Source source = new Source(true)) {
      source.failClose = true;
      Player player = new Player("");
      try {
        assertThrows(IOException.class, () -> player.playSound(source));
        assertEquals(1, player.closed);
      } finally {
        source.failClose = false;
      }
    }
  }

  @Test
  void conversionClosureDoesNotHideTheOriginalPlaybackFailure() throws Exception {
    try (Source source = new Source(true)) {
      source.failClose = true;
      Player player = new Player("open");
      try {
        IOException failure = assertThrows(IOException.class, () -> player.playSound(source));
        assertSame(player.openFailure, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, player.closed);
      } finally {
        source.failClose = false;
      }
    }
  }

  private static final class Source extends AudioInputStream {
    int closed;
    boolean failClose;

    Source(boolean convert) {
      super(
          new ByteArrayInputStream(new byte[16]),
          new AudioFormat(
              convert ? AudioFormat.Encoding.ULAW : AudioFormat.Encoding.PCM_SIGNED,
              8000,
              convert ? 8 : 16,
              1,
              convert ? 1 : 2,
              8000,
              true),
          8);
    }

    @Override
    public void close() throws IOException {
      closed++;
      super.close();
      if (failClose) {
        throw new IOException("Injected stream-close failure");
      }
    }
  }

  private static final class Player extends SoundPlayer {
    final String failure;
    final IOException openFailure = new IOException("Injected clip-open failure");
    final Clip clip;
    LineListener listener;
    int closed;

    Player(String failure) {
      super(true);
      this.failure = failure;
      clip =
          (Clip)
              Proxy.newProxyInstance(
                  Clip.class.getClassLoader(),
                  new Class<?>[] {Clip.class},
                  (proxy, method, args) -> {
                    switch (method.getName()) {
                      case "open":
                        if (failure.equals("open")) {
                          throw openFailure;
                        }
                        break;
                      case "addLineListener":
                        if (failure.equals("listener")) {
                          throw new IllegalStateException("Injected listener failure");
                        }
                        listener = (LineListener) args[0];
                        break;
                      case "start":
                        if (failure.equals("start")) {
                          throw new IllegalStateException("Injected start failure");
                        }
                        break;
                      case "close":
                        closed++;
                        break;
                      default:
                        break;
                    }
                    return null;
                  });
    }

    @Override
    Clip createClip(DataLine.Info info) throws LineUnavailableException {
      if (failure.equals("line")) {
        throw new LineUnavailableException("Injected line failure");
      }
      return clip;
    }
  }
}
