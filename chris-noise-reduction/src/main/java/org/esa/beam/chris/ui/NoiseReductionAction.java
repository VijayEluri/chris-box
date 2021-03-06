/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.beam.chris.ui;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.util.StringUtils;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;

import javax.swing.*;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action for invoking the CHRIS/Proba noise reduction.
 *
 * @author Marco Peters
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
public class NoiseReductionAction extends AbstractVisatAction {

    static final String SOURCE_NAME_PATTERN = "${sourceName}";

    private static final String DIALOG_TITLE = "CHRIS/Proba Noise Reduction";
    private static final String SOURCE_NAME_REGEX = "\\$\\{sourceName\\}";

    @Override
    public void actionPerformed(CommandEvent commandEvent) {
        final Product[] acquisitionSet = new AcquisitionSetProvider().getAcquisitionSet(getAppContext());

        final NoiseReductionPresenter presenter = new NoiseReductionPresenter(getAppContext(), 
                                                                              acquisitionSet,
                                                                              new AdvancedSettingsPresenter());
        final NoiseReductionForm noiseReductionForm = new NoiseReductionForm(presenter);
        noiseReductionForm.getTargetProductSelector().getOpenInAppCheckBox().setText(
                "Open in " + getAppContext().getApplicationName());

        final TargetProductSelectorModel targetProductSelectorModel = noiseReductionForm.getTargetProductSelectorModel();
        final ModalDialog dialog =
                new ModalDialog(VisatApp.getApp().getMainFrame(), DIALOG_TITLE, ModalDialog.ID_OK_CANCEL_HELP,
                                "chrisNoiseReductionTool") {
                    @Override
                    protected boolean verifyUserInput() {
                        if (!targetProductSelectorModel.getProductName().contains(SOURCE_NAME_PATTERN)) {
                            showErrorDialog(
                                    "Target product name must use the '" + SOURCE_NAME_PATTERN + "' expression.");
                            return false;
                        }

                        final Product[] sourceProducts = presenter.getSourceProducts();
                        if (sourceProducts.length == 0) {
                            showWarningDialog("At least one product must be selected for noise reduction.");
                            return false;
                        }
                        final File[] targetProductFiles = new File[sourceProducts.length];
                        List<String> existingFilePathList = new ArrayList<String>(7);
                        for (int i = 0; i < sourceProducts.length; ++i) {
                            targetProductFiles[i] = createResolvedTargetFile(
                                    targetProductSelectorModel.getProductFile(), sourceProducts[i].getName());
                            if (targetProductFiles[i].exists()) {
                                existingFilePathList.add(targetProductFiles[i].getAbsolutePath());
                            }
                        }
                        if (!existingFilePathList.isEmpty()) {
                            String fileList = StringUtils.arrayToString(
                                    existingFilePathList.toArray(new String[existingFilePathList.size()]), "\n");
                            String message = "The specified output file(s)\n{0}\nalready exists.\n\n" +
                                    "Do you want to overwrite the existing file(s)?";
                            String formatedMessage = MessageFormat.format(message, fileList);
                            final int answer = JOptionPane.showConfirmDialog(this.getJDialog(), formatedMessage,
                                                                             DIALOG_TITLE, JOptionPane.YES_NO_OPTION);
                            if (answer != JOptionPane.YES_OPTION) {
                                return false;
                            }
                        }

                        return true;
                    }
                };
        dialog.getJDialog().setName("chrisNoiseReductionDialog");
        dialog.setContent(noiseReductionForm);

        final String homeDirPath = SystemUtils.getUserHomeDir().getPath();
        final PropertyMap preferences = getAppContext().getPreferences();
        final String saveDirPath = preferences.getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, homeDirPath);
        targetProductSelectorModel.setProductDir(new File(saveDirPath));
        targetProductSelectorModel.setProductName(SOURCE_NAME_PATTERN + "_NR");

        if (dialog.show() == ModalDialog.ID_OK) {
            performNoiseReduction(presenter, targetProductSelectorModel);
        } else {
            for (final Product product : presenter.getDestripingFactorsSourceProducts()) {
                if (!getAppContext().getProductManager().contains(product)) {
                    product.dispose();
                }
            }
        }
    }

    @Override
    public void updateState() {
        final Product selectedProduct = getAppContext().getSelectedProduct();
        setEnabled(selectedProduct == null || new NoiseReductionProductFilter().accept(selectedProduct));
    }

    private void performNoiseReduction(NoiseReductionPresenter presenter,
                                       TargetProductSelectorModel targetProductSelectorModel) {
        final String productDirPath = targetProductSelectorModel.getProductDir().getAbsolutePath();
        final PropertyMap preferences = getAppContext().getPreferences();
        preferences.setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, productDirPath);

        final Map<Product, File> sourceProductTargetFileMap = new HashMap<Product, File>(7);
        for (final Product sourceProduct : presenter.getSourceProducts()) {
            final File unresolvedTargetFile = targetProductSelectorModel.getProductFile();
            final File targetFile = createResolvedTargetFile(unresolvedTargetFile, sourceProduct.getName());

            sourceProductTargetFileMap.put(sourceProduct, targetFile);
        }

        final NoiseReductionSwingWorker worker = new NoiseReductionSwingWorker(
                sourceProductTargetFileMap,
                presenter.getDestripingFactorsSourceProducts(),
                presenter.getDestripingFactorsParameterMap(),
                presenter.getDropoutCorrectionParameterMap(),
                createDestripingFactorsTargetFile(sourceProductTargetFileMap.values().iterator().next()),
                targetProductSelectorModel.getFormatName(),
                getAppContext(),
                targetProductSelectorModel.isOpenInAppSelected());

        worker.execute();
    }

    private File createResolvedTargetFile(File unresolvedTargetFile, String sourceProductName) {
        final String unresolvedFileName = unresolvedTargetFile.getName();
        final String resolvedFileName = unresolvedFileName.replaceAll(SOURCE_NAME_REGEX, sourceProductName);

        return new File(unresolvedTargetFile.getParentFile(), resolvedFileName);
    }


    private File createDestripingFactorsTargetFile(File targetFile) {
        final String basename = FileUtils.getFilenameWithoutExtension(targetFile);
        final String extension = FileUtils.getExtension(targetFile);

        return new File(targetFile.getParentFile(), basename + "_VSC" + extension);
    }
}
