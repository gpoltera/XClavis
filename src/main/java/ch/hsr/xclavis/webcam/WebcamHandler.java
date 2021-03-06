/*
 * Copyright (c) 2015, Gian Poltéra
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1.	Redistributions of source code must retain the above copyright notice,
 *   	this list of conditions and the following disclaimer.
 * 2.	Redistributions in binary form must reproduce the above copyright 
 *   	notice, this list of conditions and the following disclaimer in the 
 *   	documentation and/or other materials provided with the distribution.
 * 3.	Neither the name of HSR University of Applied Sciences Rapperswil nor 
 * 	the names of its contributors may be used to endorse or promote products
 * 	derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package ch.hsr.xclavis.webcam;

import ch.hsr.xclavis.qrcode.QRCodeReader;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamDiscoveryService;
import java.awt.image.BufferedImage;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/**
 * This class handles the webcams.
 *
 * @author Gian Poltéra
 */
public class WebcamHandler {

    private static final int FPS = 24;
    private static final int QRFPS = 5;
    private int sleepTimer;

    private ObservableList<DetectedWebcam> webcams;
    private Webcam selectedWebcam;
    private BufferedImage bufferedImage;
    private boolean stopCamera;
    private QRCodeReader qrCodeReader;
    private StringProperty qrResult;

    private int qrFlopsCounter;
    private IntegerProperty checkedImagesCounter;

    private Task<Void> taskInitializer, taskStream;
    private Thread threadInitializer, threadStream;

    /**
     * Creates a new WebcamHandler.
     */
    public WebcamHandler() {
        this.webcams = FXCollections.observableArrayList();
        this.sleepTimer = 1000 / FPS;
        this.qrFlopsCounter = 0;
        this.checkedImagesCounter = new SimpleIntegerProperty(0);
        this.selectedWebcam = null;
        this.stopCamera = false;
        scanWebcams();
        this.qrCodeReader = new QRCodeReader();
        this.qrResult = new SimpleStringProperty("");
    }

    /**
     * Checks if a webcam exists.
     *
     * @return true, if a webcam exists or false otherwise
     */
    public boolean existsWebcam() {
        if (webcams.size() > 0) {
            return true;
        }

        return false;
    }

    /**
     * Gets the number of webcams detected.
     *
     * @return the number of detected webcams as a integer
     */
    public int getWebcamCount() {
        return webcams.size();
    }

    /**
     * Gets the DetectedWebcam's as a list.
     *
     * @return the detectedWebcam's as a ObservableList
     */
    public ObservableList<DetectedWebcam> getWebcams() {
        return webcams;
    }

    /**
     * Initializes a webcam.
     *
     * @param webcamIndex the index of the webcam to be initialized
     */
    public void initWebcam(final int webcamIndex) {
        taskInitializer = new Task<Void>() {

            @Override
            protected Void call() throws Exception {
                if (!(selectedWebcam == null)) {
                    close();
                }
                selectedWebcam = Webcam.getWebcams().get(webcamIndex);
                selectedWebcam.open();
                return null;
            }
        };
        threadInitializer = new Thread(taskInitializer);
        threadInitializer.setDaemon(true);
        threadInitializer.start();
    }

    /**
     * Gets the webcam stream as an image.
     *
     * @return the webcam stream as a ObjectProperty
     */
    public ObjectProperty<Image> getStream() {
        stopCamera = false;
        ObjectProperty<Image> imageProperty = new SimpleObjectProperty<>();
        taskStream = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                while (!stopCamera) {
                    if (isCancelled()) {
                        break;
                    }
                    if (selectedWebcam.isOpen()) {
                        try {
                            if ((bufferedImage = selectedWebcam.getImage()) != null) {
                                Platform.runLater(() -> {
                                    // Convert the Image for JavaFX
                                    Image image = SwingFXUtils.toFXImage(bufferedImage, null);
                                    imageProperty.set(image);
                                    qrFlopsCounter++;
                                    if (qrFlopsCounter == 5) {
                                        checkedImagesCounter.set(checkedImagesCounter.get() + 1);
                                        // Check if QR-Code is in Image
                                        if (qrCodeReader.checkImage(bufferedImage)) {
                                            if (qrResult.get().equals(qrCodeReader.getResult())) {
                                                qrResult.set("");
                                                qrResult.set(qrCodeReader.getResult());
                                            } else {
                                                qrResult.set(qrCodeReader.getResult());
                                            }
                                        }
                                        qrFlopsCounter = 0;
                                    }

                                });
                            }
                        } catch (Exception e) {
                        } finally {
                        }
                    }
                    // Sleep Timer for FPS
                    Thread.sleep(sleepTimer);
                }

                return null;
            }
        };
        threadStream = new Thread(taskStream);

        threadStream.setDaemon(
                true);
        threadStream.start();

        return imageProperty;
    }

    /**
     * Gets a scanned QR-Code.
     *
     * @return the scanned QR-Code as a StringProperty
     */
    public StringProperty getScanedQRCode() {
        return qrResult;
    }

    /**
     * Gets the counter of the already checked images.
     *
     * @return the counter of the checked images
     */
    public ReadOnlyIntegerProperty getCheckedImagesCounter() {
        return checkedImagesCounter;
    }

    /**
     * Stops the actual running webcam.
     */
    public void stopWebcam() {
        stopCamera = true;
        close();
        if (taskInitializer != null && taskInitializer.isRunning()) {
            taskInitializer.cancel();
        }
        if (taskStream != null && taskStream.isRunning()) {
            taskStream.cancel();
        }
        if (threadInitializer != null && threadInitializer.isAlive()) {
            threadInitializer.interrupt();
        }
        if (threadStream != null && threadStream.isAlive()) {
            threadStream.interrupt();
        }
        WebcamDiscoveryService discovery = Webcam.getDiscoveryService();
        discovery.stop();
    }

    private void scanWebcams() {
        int webcamCounter = 0;

        for (Webcam webcam : Webcam.getWebcams()) {
            DetectedWebcam webcamInfo = new DetectedWebcam(webcamCounter, webcam.getName());
            webcams.add(webcamInfo);
            webcamCounter++;
        }
//        Stop searching Webcams
//        WebcamDiscoveryService discovery = Webcam.getDiscoveryService();
//        discovery.stop();
    }

    private void close() {
        if (selectedWebcam != null) {
            selectedWebcam.close();
        }
    }
}
