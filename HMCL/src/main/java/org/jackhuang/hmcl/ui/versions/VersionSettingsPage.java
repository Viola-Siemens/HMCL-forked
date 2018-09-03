/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ImagePickerItem;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.JavaVersion;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionSettingsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty();

    private VersionSetting lastVersionSetting = null;
    private Profile profile;
    private String versionId;
    private boolean javaItemsLoaded;
    private InvalidationListener specificSettingsListener;

    @FXML private VBox rootPane;
    @FXML private ScrollPane scroll;
    @FXML private JFXTextField txtWidth;
    @FXML private JFXTextField txtHeight;
    @FXML private JFXTextField txtMaxMemory;
    @FXML private JFXTextField txtJVMArgs;
    @FXML private JFXTextField txtGameArgs;
    @FXML private JFXTextField txtMetaspace;
    @FXML private JFXTextField txtWrapper;
    @FXML private JFXTextField txtPrecallingCommand;
    @FXML private JFXTextField txtServerIP;
    @FXML private ComponentList advancedSettingsPane;
    @FXML private ComponentList componentList;
    @FXML private JFXComboBox<?> cboLauncherVisibility;
    @FXML private JFXCheckBox chkFullscreen;
    @FXML private Label lblPhysicalMemory;
    @FXML private JFXToggleButton chkNoJVMArgs;
    @FXML private JFXToggleButton chkNoGameCheck;
    @FXML private MultiFileItem<JavaVersion> javaItem;
    @FXML private MultiFileItem<EnumGameDirectory> gameDirItem;
    @FXML private JFXToggleButton chkShowLogs;
    @FXML private ImagePickerItem iconPickerItem;
    @FXML private JFXCheckBox chkEnableSpecificSettings;
    @FXML private BorderPane settingsTypePane;

    public VersionSettingsPage() {
        FXUtils.loadFXML(this, "/assets/fxml/version/version-settings.fxml");
    }

    @FXML
    private void initialize() {
        lblPhysicalMemory.setText(i18n("settings.physical_memory") + ": " + OperatingSystem.TOTAL_MEMORY + "MB");

        FXUtils.smoothScrolling(scroll);

        Task.of(variables -> variables.set("list", JavaVersion.getJREs()))
                .subscribe(Schedulers.javafx(), variables -> {
                    javaItem.loadChildren(
                            (variables.<List<JavaVersion>>get("list")).stream()
                                    .map(javaVersion -> javaItem.createChildren(javaVersion.getVersion(), javaVersion.getBinary().getAbsolutePath(), javaVersion))
                                    .collect(Collectors.toList()));
                    javaItemsLoaded = true;
                    initializeSelectedJava();
                });

        javaItem.setSelectedData(null);
        javaItem.setFallbackData(JavaVersion.fromCurrentEnvironment());
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            javaItem.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java", "java.exe", "javaw.exe"));

        gameDirItem.setCustomUserData(EnumGameDirectory.CUSTOM);
        gameDirItem.loadChildren(Arrays.asList(
                gameDirItem.createChildren(i18n("settings.advanced.game_dir.default"), EnumGameDirectory.ROOT_FOLDER),
                gameDirItem.createChildren(i18n("settings.advanced.game_dir.independent"), EnumGameDirectory.VERSION_FOLDER)
        ));

        chkEnableSpecificSettings.selectedProperty().addListener((a, b, newValue) -> {
            if (versionId == null) return;

            // do not call versionSettings.setUsesGlobal(true/false)
            // because versionSettings can be the global one.
            // global versionSettings.usesGlobal is always true.
            if (newValue)
                profile.specializeVersionSetting(versionId);
            else
                profile.globalizeVersionSetting(versionId);

            Platform.runLater(() -> loadVersionSetting(profile, versionId));
        });

        specificSettingsListener = o -> {
            chkEnableSpecificSettings.setSelected(!lastVersionSetting.isUsesGlobal());
        };

        componentList.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
        advancedSettingsPane.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
    }

    public void loadVersionSetting(Profile profile, String versionId) {
        this.profile = profile;
        this.versionId = versionId;

        if (versionId == null) {
            componentList.getContent().remove(iconPickerItem);
            rootPane.getChildren().remove(settingsTypePane);
            chkEnableSpecificSettings.setSelected(true);
        }

        VersionSetting versionSetting = profile.getVersionSetting(versionId);

        gameDirItem.setDisable(versionId != null && profile.getRepository().isModpack(versionId));
        settingsTypePane.setDisable(versionId != null && profile.getRepository().isModpack(versionId));

        // unbind data fields
        if (lastVersionSetting != null) {
            FXUtils.unbindInt(txtWidth, lastVersionSetting.widthProperty());
            FXUtils.unbindInt(txtHeight, lastVersionSetting.heightProperty());
            FXUtils.unbindInt(txtMaxMemory, lastVersionSetting.maxMemoryProperty());
            FXUtils.unbindString(javaItem.getTxtCustom(), lastVersionSetting.javaDirProperty());
            FXUtils.unbindString(gameDirItem.getTxtCustom(), lastVersionSetting.gameDirProperty());
            FXUtils.unbindString(txtJVMArgs, lastVersionSetting.javaArgsProperty());
            FXUtils.unbindString(txtGameArgs, lastVersionSetting.minecraftArgsProperty());
            FXUtils.unbindString(txtMetaspace, lastVersionSetting.permSizeProperty());
            FXUtils.unbindString(txtWrapper, lastVersionSetting.wrapperProperty());
            FXUtils.unbindString(txtPrecallingCommand, lastVersionSetting.preLaunchCommandProperty());
            FXUtils.unbindString(txtServerIP, lastVersionSetting.serverIpProperty());
            FXUtils.unbindBoolean(chkFullscreen, lastVersionSetting.fullscreenProperty());
            FXUtils.unbindBoolean(chkNoGameCheck, lastVersionSetting.notCheckGameProperty());
            FXUtils.unbindBoolean(chkNoJVMArgs, lastVersionSetting.noJVMArgsProperty());
            FXUtils.unbindBoolean(chkShowLogs, lastVersionSetting.showLogsProperty());
            FXUtils.unbindEnum(cboLauncherVisibility);

            lastVersionSetting.usesGlobalProperty().removeListener(specificSettingsListener);

            gameDirItem.selectedDataProperty().unbindBidirectional(lastVersionSetting.gameDirTypeProperty());
            gameDirItem.subtitleProperty().unbind();
        }

        // unbind data fields
        javaItem.setToggleSelectedListener(null);

        // bind new data fields
        FXUtils.bindInt(txtWidth, versionSetting.widthProperty());
        FXUtils.bindInt(txtHeight, versionSetting.heightProperty());
        FXUtils.bindInt(txtMaxMemory, versionSetting.maxMemoryProperty());
        FXUtils.bindString(javaItem.getTxtCustom(), versionSetting.javaDirProperty());
        FXUtils.bindString(gameDirItem.getTxtCustom(), versionSetting.gameDirProperty());
        FXUtils.bindString(txtJVMArgs, versionSetting.javaArgsProperty());
        FXUtils.bindString(txtGameArgs, versionSetting.minecraftArgsProperty());
        FXUtils.bindString(txtMetaspace, versionSetting.permSizeProperty());
        FXUtils.bindString(txtWrapper, versionSetting.wrapperProperty());
        FXUtils.bindString(txtPrecallingCommand, versionSetting.preLaunchCommandProperty());
        FXUtils.bindString(txtServerIP, versionSetting.serverIpProperty());
        FXUtils.bindBoolean(chkFullscreen, versionSetting.fullscreenProperty());
        FXUtils.bindBoolean(chkNoGameCheck, versionSetting.notCheckGameProperty());
        FXUtils.bindBoolean(chkNoJVMArgs, versionSetting.noJVMArgsProperty());
        FXUtils.bindBoolean(chkShowLogs, versionSetting.showLogsProperty());
        FXUtils.bindEnum(cboLauncherVisibility, versionSetting.launcherVisibilityProperty());

        versionSetting.usesGlobalProperty().addListener(specificSettingsListener);
        if (versionId != null)
            chkEnableSpecificSettings.setSelected(!versionSetting.isUsesGlobal());

        javaItem.setToggleSelectedListener(newValue -> {
            if (javaItem.isCustomToggle(newValue)) {
                versionSetting.setUsesCustomJavaDir();
            } else {
                versionSetting.setJavaVersion((JavaVersion) newValue.getUserData());
            }
        });

        versionSetting.javaDirProperty().setChangedListener(it -> initJavaSubtitle(versionSetting));
        versionSetting.javaProperty().setChangedListener(it -> initJavaSubtitle(versionSetting));
        initJavaSubtitle(versionSetting);

        gameDirItem.selectedDataProperty().bindBidirectional(versionSetting.gameDirTypeProperty());
        gameDirItem.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath()).normalize().toString(),
                versionSetting.gameDirProperty(), versionSetting.gameDirTypeProperty()));

        lastVersionSetting = versionSetting;

        initializeSelectedJava();

        loadIcon();
    }

    private void initializeSelectedJava() {
        if (lastVersionSetting == null
                || !javaItemsLoaded /* JREs are still being loaded */) {
            return;
        }

        if (lastVersionSetting.isUsesCustomJavaDir()) {
            javaItem.setSelectedData(null);
        } else {
            try {
                javaItem.setSelectedData(lastVersionSetting.getJavaVersion());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initJavaSubtitle(VersionSetting versionSetting) {
        Task.of(variables -> variables.set("java", versionSetting.getJavaVersion()))
                .subscribe(Task.of(Schedulers.javafx(),
                        variables -> javaItem.setSubtitle(variables.<JavaVersion>getOptional("java")
                                .map(JavaVersion::getBinary).map(File::getAbsolutePath).orElse("Invalid Java Directory"))));
    }

    @FXML
    private void editGlobalSettings() {
        Versions.modifyGlobalSettings(profile);
    }

    @FXML
    private void onExploreIcon() {
        if (versionId == null)
            return;

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.png"), "*.png"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile != null) {
            File iconFile = profile.getRepository().getVersionIcon(versionId);
            try {
                FileUtils.copyFile(selectedFile, iconFile);
                loadIcon();
            } catch (IOException e) {
                Logging.LOG.log(Level.SEVERE, "Failed to copy icon file from " + selectedFile + " to " + iconFile, e);
            }
        }
    }

    private void loadIcon() {
        if (versionId == null) {
            iconPickerItem.setImage(new Image("/assets/img/grass.png"));
            return;
        }

        File iconFile = profile.getRepository().getVersionIcon(versionId);
        if (iconFile.exists())
            iconPickerItem.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        else
            iconPickerItem.setImage(new Image("/assets/img/grass.png"));
        FXUtils.limitSize(iconPickerItem.getImageView(), 32, 32);
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }
}