/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.utils;

import java.io.IOException;
import java.net.URL;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * A few simple sounds played by Nodus.
 *
 * @author Bart Jourquin
 */
public class SoundPlayer {

  /** Played after a long SQL query. */
  public static final int SOUND_DING = 4;

  /** Played if an assignment fails. */
  public static final int SOUND_FAILURE = 2;

  /** Played if an assignment succeeds. */
  public static final int SOUND_OK = 1;

  private boolean withSound;

  /**
   * Initializes the sound player.
   *
   * @param withSound If true, Nodus with play the sounds.
   */
  public SoundPlayer(boolean withSound) {
    this.withSound = withSound;
  }

  /**
   * Enables or disables the sound player.
   *
   * @param withSound If true, sounds will be played.
   */
  public void enableSound(boolean withSound) {
    this.withSound = withSound;
  }

  /**
   * Play a given sound.
   *
   * @param soundId The sound ID to play.
   */
  public void play(int soundId) {

    if (!withSound) {
      return;
    }
    String soundFile = "";

    switch (soundId) {
      case SOUND_OK:
        soundFile = "ok.wav";
        break;
      case SOUND_FAILURE:
        soundFile = "nok.wav";
        break;
      case SOUND_DING:
        soundFile = "ding.wav";
        break;

      default:
        return;
    }

    URL soundUrl = getClass().getResource(soundFile);
    if (soundUrl == null) {
      System.err.println("Sound resource not found: " + soundFile);
      return;
    }

    try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundUrl)) {
      playSound(audioStream);
    } catch (UnsupportedAudioFileException e) {
      e.printStackTrace();
    } catch (LineUnavailableException e) {
      // Sound is optional. Keep the previous behavior and do not display an error dialog.
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Plays the sound.
   *
   * @param sourceStream Audio stream to load in a clip.
   * @throws IOException On I/O error.
   * @throws LineUnavailableException If no clip line is available.
   */
  private void playSound(AudioInputStream sourceStream)
      throws IOException, LineUnavailableException {

    // At present, ALAW and ULAW encodings must be converted to PCM_SIGNED before they can be
    // played.
    AudioFormat format = sourceStream.getFormat();
    AudioInputStream playbackStream = sourceStream;

    if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
      format =
          new AudioFormat(
              AudioFormat.Encoding.PCM_SIGNED,
              format.getSampleRate(),
              format.getSampleSizeInBits() * 2,
              format.getChannels(),
              format.getFrameSize() * 2,
              format.getFrameRate(),
              true); // big endian
      playbackStream = AudioSystem.getAudioInputStream(format, sourceStream);
    }

    // Create the clip
    DataLine.Info info =
        new DataLine.Info(
            Clip.class, playbackStream.getFormat(), getClipBufferSize(playbackStream, format));

    final Clip clip = (Clip) AudioSystem.getLine(info);

    try {
      // This method does not return until the audio file is completely loaded.
      clip.open(playbackStream);
    } catch (IOException | LineUnavailableException e) {
      clip.close();
      throw e;
    } finally {
      if (playbackStream != sourceStream) {
        playbackStream.close();
      }
    }

    clip.addLineListener(
        event -> {
          if (event.getType() == LineEvent.Type.STOP) {
            event.getLine().close();
          }
        });

    // Start playing. The line listener releases the clip when playback stops.
    clip.start();
  }

  /**
   * Returns the clip buffer size when it is known.
   *
   * @param stream Audio stream.
   * @param format Audio format.
   * @return Buffer size in bytes, or {@link AudioSystem#NOT_SPECIFIED}.
   */
  private int getClipBufferSize(AudioInputStream stream, AudioFormat format) {
    long frameLength = stream.getFrameLength();
    if (frameLength == AudioSystem.NOT_SPECIFIED) {
      return AudioSystem.NOT_SPECIFIED;
    }

    long bufferSize = frameLength * format.getFrameSize();
    if (bufferSize > Integer.MAX_VALUE) {
      return AudioSystem.NOT_SPECIFIED;
    }

    return (int) bufferSize;
  }
}
