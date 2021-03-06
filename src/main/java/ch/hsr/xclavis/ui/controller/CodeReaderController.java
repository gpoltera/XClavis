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
package ch.hsr.xclavis.ui.controller;

import ch.hsr.xclavis.keys.ECDHKey;
import ch.hsr.xclavis.commons.InputBlock;
import ch.hsr.xclavis.commons.InputBlocks;
import ch.hsr.xclavis.keys.Key;
import ch.hsr.xclavis.keys.PrivaSphereKey;
import ch.hsr.xclavis.keys.SessionID;
import ch.hsr.xclavis.keys.SessionKey;
import ch.hsr.xclavis.qrcode.QRModel;
import ch.hsr.xclavis.webcam.DetectedWebcam;
import ch.hsr.xclavis.ui.MainApp;
import ch.hsr.xclavis.webcam.WebcamHandler;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * FXML Controller class Shows the webcam and manual code input.
 *
 * @author Gian Poltéra
 */
public class CodeReaderController implements Initializable {

    private final static String PATTERN = "[23456789abcdefghjklmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ]";

    private MainApp mainApp;
    private ResourceBundle rb;
    private WebcamHandler webcamHandler;
    private final int blockLength = 5;
    private final int blockChecksumSize = 1;
    private SessionID manualSessionID;
    private List<Key> ecdhResponseKeys;
    private boolean webcamStarted;
    private int imageCounter = 0;
    private ImageView imageViewWebcam;

    @FXML
    private VBox vbWebcamInput;
    @FXML
    private HBox hbWebcamSelecter;
    @FXML
    private HBox hbWebcamImage;
    @FXML
    private Label lblWebcamLoad;
    @FXML
    private VBox vbManualInput;
    @FXML
    private HBox hbInputSelecter;
    @FXML
    private VBox vbKeyInput;
    @FXML
    private HBox hbInputBlocks1;
    @FXML
    private HBox hbInputBlocks2;
    @FXML
    private HBox hbInputBlocks3;
    @FXML
    private HBox hbInputBlocks4;
    @FXML
    private ComboBox<DetectedWebcam> cbWebcamSelecter;
    @FXML
    private Label lblNoWebcam;
    @FXML
    private Label lblWebcamQRState;

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.rb = rb;
        ecdhResponseKeys = new ArrayList<>();
        webcamStarted = false;

        InputBlock inputSelecter = new InputBlock(blockLength, blockChecksumSize, PATTERN);
        inputSelecter.textProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
            if (inputSelecter.isValid()) {
                String type = inputSelecter.getValue().substring(0, 1);
                String random = inputSelecter.getValue().substring(1);
                manualSessionID = new SessionID(type, random);
                addBlocks(type);
            }
        });
        hbInputSelecter.getChildren().add(inputSelecter);
        vbKeyInput.getChildren().removeAll(hbInputBlocks1, hbInputBlocks2, hbInputBlocks3, hbInputBlocks4);
        vbManualInput.getChildren().remove(vbKeyInput);
    }

    /**
     * Is called by the main application to give a reference back to itself.
     *
     * @param mainApp
     */
    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    /**
     * Starts the webcam.
     */
    public void startWebcam() {
        if (!webcamStarted) {
            webcamHandler = new WebcamHandler();
            imageViewWebcam = new ImageView();
            imageViewWebcam.setFitWidth(480);
            imageViewWebcam.setPreserveRatio(true);

            // Show WebcamSelecter if more then one Webcam
            if (webcamHandler.getWebcamCount() > 1) {
                cbWebcamSelecter.setItems(webcamHandler.getWebcams());
                cbWebcamSelecter.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends DetectedWebcam> observable, DetectedWebcam oldValue, DetectedWebcam newValue) -> {
                    if (newValue != null) {
                        webcamHandler.initWebcam(newValue.getWebcamIndex());
                        imageViewWebcam.imageProperty().bind(webcamHandler.getStream());
                    }
                });
            } else {
                vbWebcamInput.getChildren().remove(hbWebcamSelecter);
            }

            // Show WebcamImage from first Webcam if one Webcam exists
            if (webcamHandler.existsWebcam()) {
                lblWebcamQRState.setText(rb.getString("webcam_load"));
                lblWebcamQRState.setTextFill(Color.BLACK);
                lblWebcamQRState.setStyle("-fx-font-weight: normal");
                lblWebcamQRState.setVisible(true);
                // Start the Webcam
                webcamStarted = true;
                webcamHandler.initWebcam(0);
                imageViewWebcam.imageProperty().bind(webcamHandler.getStream());
                hbWebcamImage.getChildren().remove(lblWebcamLoad);
                vbWebcamInput.getChildren().remove(lblNoWebcam);
                hbWebcamImage.getChildren().add(imageViewWebcam);

            } else {
                vbWebcamInput.getChildren().remove(hbWebcamImage);
                vbWebcamInput.getChildren().remove(lblWebcamQRState);
            }

            // Listener for QRCode
            webcamHandler.getScanedQRCode().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
                QRModel qrModel = new QRModel();
                if (qrModel.isStandardKey(newValue)) {
                    // Standard Key
                    String[][] keys = qrModel.getStandardKeys(newValue);
                    if (keys.length > 0) {
                        ecdhResponseKeys.clear();
                        for (int i = 0; i < keys.length; i++) {
                            SessionID sessionID = new SessionID(keys[i][0].substring(0, 1), keys[i][0].substring(1));
                            String key = keys[i][1];
                            addKeyToKeyStore(sessionID, key);
                        }
                        if (ecdhResponseKeys.size() > 0) {
                            mainApp.showCodeOutput(ecdhResponseKeys);
                        } else {
                            mainApp.showKeyManagement();
                        }
                    }
                    lblWebcamQRState.setVisible(false);
                    imageCounter = 0;
                } else if (qrModel.isPrivaSphereKey(newValue)) {
                    // PrivaSphere Key
                    PrivaSphereKey privaSphereKey = qrModel.getPrivaSphereKey(newValue);
                    privaSphereKey.setLastUseDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    privaSphereKey.setLastActivity(Key.READING);
                    mainApp.getKeys().add(privaSphereKey);
                    mainApp.showKeyManagement();
                    
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle(rb.getString("window_title"));
                    alert.setHeaderText(rb.getString("privasphere_key"));
                    alert.setContentText(rb.getString("privasphere_sender") + ": " + privaSphereKey.getPartner() + "\n"
                            + rb.getString("privasphere_id") + ": " + privaSphereKey.getID().substring(1) + "\n"
                            + rb.getString("date") + ": " + privaSphereKey.getCreationDate() + "\n"
                            + rb.getString("key") + ": " + privaSphereKey.getKey());
                    ButtonType btCopyToClipboard = new ButtonType(rb.getString("copy_to_clipboard"));
                    ButtonType btClose = new ButtonType(rb.getString("close"), ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(btCopyToClipboard, btClose);
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.get() == btCopyToClipboard) {
                        final Clipboard clipboard = Clipboard.getSystemClipboard();
                        final ClipboardContent content = new ClipboardContent();
                        content.putString(privaSphereKey.getKey());
                        clipboard.setContent(content);
                    }
                    lblWebcamQRState.setVisible(false);
                    imageCounter = 0;
                } else {
                    // QR-code found, but not in XClavis format
                    lblWebcamQRState.setText(rb.getString("not_right_qr_format"));
                    lblWebcamQRState.setTextFill(Color.RED);
                    lblWebcamQRState.setStyle("-fx-font-weight: bold");
                    lblWebcamQRState.setVisible(true);
                }
            });

            // Listener for scanned images counter
            webcamHandler.getCheckedImagesCounter().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
                imageCounter++;
                if ((imageCounter % 10) == 0) {
                    lblWebcamQRState.setText(imageCounter + " " + rb.getString("images_checked"));
                    lblWebcamQRState.setTextFill(Color.BLACK);
                    lblWebcamQRState.setStyle("-fx-font-weight: normal");
                    lblWebcamQRState.setVisible(true);
                }
            });
        }
    }

    /**
     * Stop the webcam.
     */
    public void stopWebcam() {
        if (webcamHandler != null) {
            webcamHandler.stopWebcam();
        }
        webcamStarted = false;
        if (imageViewWebcam != null) {
            imageViewWebcam.imageProperty().unbind();
        }
        if (hbWebcamImage.getChildren().contains(imageViewWebcam)) {
            hbWebcamImage.getChildren().remove(imageViewWebcam);
        }
    }

    private void addBlocks(String type) {
        int blocksSize = 7;
        int overallChecksumSize = 2;
        switch (type) {
            case SessionID.SESSION_KEY_128:
                blocksSize = 7;
                overallChecksumSize = 2;
                vbKeyInput.getChildren().add(hbInputBlocks1);
                break;
            case SessionID.SESSION_KEY_256:
                blocksSize = 14;
                overallChecksumSize = 4;
                vbKeyInput.getChildren().add(hbInputBlocks1);
                vbKeyInput.getChildren().add(hbInputBlocks2);
                break;
            case SessionID.ECDH_REQ_256:
            case SessionID.ECDH_RES_256:
                blocksSize = 14;
                overallChecksumSize = 3;
                vbKeyInput.getChildren().add(hbInputBlocks1);
                vbKeyInput.getChildren().add(hbInputBlocks2);
                break;
            case SessionID.ECDH_REQ_512:
            case SessionID.ECDH_RES_512:
                blocksSize = 27;
                overallChecksumSize = 4;
                vbKeyInput.getChildren().add(hbInputBlocks1);
                vbKeyInput.getChildren().add(hbInputBlocks2);
                vbKeyInput.getChildren().add(hbInputBlocks3);
                vbKeyInput.getChildren().add(hbInputBlocks4);
                break;
        }

        InputBlocks inputBlocks = new InputBlocks(blocksSize, overallChecksumSize);

        for (int i = 0; i < blocksSize; i++) {
            InputBlock inputBlock = new InputBlock(blockLength, blockChecksumSize, PATTERN);
            inputBlock.textProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
                if (inputBlocks.areValid()) {
                    ecdhResponseKeys.clear();
                    addKeyToKeyStore(manualSessionID, newValue);
                    if (ecdhResponseKeys.size() > 0) {
                        mainApp.showCodeOutput(ecdhResponseKeys);
                    } else {
                        mainApp.showKeyManagement();
                    }
                }
            });
            inputBlocks.addBlock(inputBlock);
            if (i < 7) {
                hbInputBlocks1.getChildren().add(inputBlock);
            } else if (i < 14) {
                hbInputBlocks2.getChildren().add(inputBlock);
            } else if (i < 21) {
                hbInputBlocks3.getChildren().add(inputBlock);
            } else if (i < 28) {
                hbInputBlocks4.getChildren().add(inputBlock);
            }
        }
        vbManualInput.getChildren().add(vbKeyInput);
    }

    private void addKeyToKeyStore(SessionID sessionID, String key) {
        if (sessionID.isSessionKey()) {
            SessionKey sessionKey = new SessionKey(sessionID, key);
            sessionKey.setPartner("Remote");
            sessionKey.setState(Key.REMOTE);
            mainApp.getKeys().add(sessionKey);

        } else if (sessionID.isECDHReq()) {
            // Calculating the ECDH response and the SessionKey
            ECDHKey ecdhKey = new ECDHKey(sessionID);
            //mainApp.getKeys().add(ecdhKey);
            SessionKey sessionKey = ecdhKey.getSessionKey(key);
            sessionKey.setPartner("Remote");
            sessionKey.setState(Key.REMOTE);
            mainApp.getKeys().add(sessionKey);
            mainApp.getKeys().remove(ecdhKey);
            ecdhResponseKeys.add(ecdhKey);

        } else if (sessionID.isECDHRes()) {
            // Load the ECDHKey from the KeyStore
            ECDHKey ecdhKey = mainApp.getKeys().getECDHKey(sessionID.getRandom());
            SessionKey sessionKey = ecdhKey.getSessionKey(key);
            sessionKey.setPartner(ecdhKey.getPartner());
            sessionKey.setState(Key.USABLE);
            mainApp.getKeys().add(sessionKey);
            mainApp.getKeys().remove(ecdhKey);
        }
    }
}
