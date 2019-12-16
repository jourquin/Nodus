/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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
import java.util.TimerTask;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
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

  private AudioInputStream stream;

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

    // From file
    try {
      stream = AudioSystem.getAudioInputStream(getClass().getResource(soundFile));
    } catch (UnsupportedAudioFileException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    playSound();
  }

  /** Plays the sound. */
  private void playSound() {

    try { // At present, ALAW and ULAW encodings must be converted
      // to PCM_SIGNED before it can be played
      AudioFormat format = stream.getFormat();
      if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
        format =
            new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.getSampleRate(),
                format.getSampleSizeInBits() * 2,
                format.getChannels(),
                format.getFrameSize() * 2,
                format.getFrameRate(),
                true); // big
        // endian
        stream = AudioSystem.getAudioInputStream(format, stream);
      }

      // Create the clip
      DataLine.Info info =
          new DataLine.Info(
              Clip.class,
              stream.getFormat(),
              (int) stream.getFrameLength() * format.getFrameSize());

      final Clip clip;

      clip = (Clip) AudioSystem.getLine(info);

      // This method does not return until the audio file is completely
      // loaded
      clip.open(stream);

      // Length of clip in milliseconds
      long length = clip.getMicrosecondLength() / 1000;

      // Start playing
      clip.start();

      // Release the resource one second after the sound is played
      java.util.Timer closeTimer;
      closeTimer = new java.util.Timer();
      closeTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              clip.close();
            }
          },
          length + 1000);
    } catch (LineUnavailableException e) { 
      //e.printStackTrace();
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }
}
