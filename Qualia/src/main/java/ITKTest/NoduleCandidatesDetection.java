package ITKTest;

import org.itk.itkanisotropicsmoothing.itkGradientAnisotropicDiffusionImageFilterIF3IF3;
import org.itk.itkcommon.itkImageSS3;
import org.itk.itkcommon.itkImageUC3;
import org.itk.itkcommon.itkVectorD3;
import org.itk.itkimagefilterbase.itkCastImageFilterIF3ISS3;
import org.itk.itkimagefilterbase.itkCastImageFilterISS3IF3;
import org.itk.itkimageintensity.itkAddImageFilterISS3ISS3ISS3;
import org.itk.itkimageintensity.itkMaskImageFilterISS3IUC3ISS3;
import org.itk.itklabelmap.*;

/**
 * <pre>
 * kr.qualia
 * NoduleCandidatesDetection.java
 * FIXME 클래스 설명
 * </pre>
 *
 * @author taznux
 * @date 2014. 4. 17.
 */
public class NoduleCandidatesDetection implements Runnable {
    private itkImageSS3 lungImage_;
    private itkImageSS3 noduleCandidatesLabel_;
    private itkImageUC3 lungMask_;
    private itkImageUC3 noduleCandidatesMask_;
    private itkLabelMap3 noduleCandidates_;
    private itkImageUC3 vesselMask_;
    private itkLabelMap3 vesselMap_;

    public NoduleCandidatesDetection() {
        lungImage_ = null;
        lungMask_ = null;
        noduleCandidatesMask_ = null;
        noduleCandidates_ = null;
        vesselMask_ = null;
        vesselMap_ = null;
    }

    public void setLungImage(itkImageSS3 lungImage) {
        lungImage_ = lungImage;
    }

    public void setLungMask(itkImageUC3 lungMask) {
        lungMask_ = lungMask;
    }

    public itkImageUC3 getNoduleCandidatesMask() {
        return noduleCandidatesMask_;
    }

    public itkLabelMap3 getNoduleCandidates() {
        return noduleCandidates_;
    }

    public itkImageUC3 getVesselMask() {
        return vesselMask_;
    }

    public itkLabelMap3 getVesselMap() {
        return vesselMap_;
    }


    public void run() {
        ImageProcessingUtils.tic();

		/* Lung Masking */
        itkMaskImageFilterISS3IUC3ISS3 maskFilter = new itkMaskImageFilterISS3IUC3ISS3();
        maskFilter.SetInput1(lungImage_);
        maskFilter.SetInput2(lungMask_);
        maskFilter.SetOutsideValue((short) -2000);
        maskFilter.Update();

        itkImageSS3 lungSegImage;
        lungSegImage = maskFilter.GetOutput();

        multiThresholdDetection(lungSegImage);

        System.out.println("Vessel Objects " + vesselMap_.GetNumberOfLabelObjects());
        System.out.println("Objects " + noduleCandidates_.GetNumberOfLabelObjects());

        itkMaskImageFilterISS3IUC3ISS3 maskImageFilter = new itkMaskImageFilterISS3IUC3ISS3();
        itkMaskImageFilterISS3IUC3ISS3 maskImageFilter1 = new itkMaskImageFilterISS3IUC3ISS3();
        itkAddImageFilterISS3ISS3ISS3 addImageFilter = new itkAddImageFilterISS3ISS3ISS3();
        itkAddImageFilterISS3ISS3ISS3 addImageFilter1 = new itkAddImageFilterISS3ISS3ISS3();

        maskImageFilter.SetConstant1((short) 1500);
        maskImageFilter.SetMaskImage(noduleCandidatesMask_);
        maskImageFilter1.SetConstant1((short) 500);
        maskImageFilter1.SetMaskImage(vesselMask_);

        addImageFilter.SetInput1(lungSegImage);
        addImageFilter.SetInput2(maskImageFilter.GetOutput());
        addImageFilter1.SetInput1(addImageFilter.GetOutput());
        addImageFilter1.SetInput2(maskImageFilter1.GetOutput());

        maskImageFilter.SetOutsideValue((short) -500);
        maskImageFilter1.SetOutsideValue((short) -500);

        addImageFilter1.Update();

        noduleCandidatesLabel_ = addImageFilter1.GetOutput();
    }

    /**
     * <pre>
     * 1.개요 : FIXME
     * 2.처리내용 : FIXME
     * </pre>
     *
     * @param lungSegImage
     * @return
     * @method multiThresholdDetection
     */
    private void multiThresholdDetection(itkImageSS3 lungSegImage) {
        itkCastImageFilterISS3IF3 castImageFilterISS3IF3 = new itkCastImageFilterISS3IF3();
        itkGradientAnisotropicDiffusionImageFilterIF3IF3 gradientAnisotropicDiffusionImageFilter = new itkGradientAnisotropicDiffusionImageFilterIF3IF3();
        itkCastImageFilterIF3ISS3 castImageFilterIF3ISS3 = new itkCastImageFilterIF3ISS3();

        long new_label = 0;
        long new_vlabel = 0;

        int step = 16;
        int maxThreshold = 0;
        int minThreshold = -800;
        int gap = (maxThreshold - minThreshold) / step;

        noduleCandidates_ = new itkLabelMap3();
        vesselMap_ = new itkLabelMap3();

        vesselMap_.CopyInformation(lungSegImage);
        noduleCandidates_.CopyInformation(lungSegImage);

        castImageFilterISS3IF3.SetInput(lungSegImage);
        gradientAnisotropicDiffusionImageFilter.SetInput(castImageFilterISS3IF3.GetOutput());
        castImageFilterIF3ISS3.SetInput(gradientAnisotropicDiffusionImageFilter.GetOutput());

        gradientAnisotropicDiffusionImageFilter.SetNumberOfIterations(5);
        gradientAnisotropicDiffusionImageFilter.SetTimeStep(0.03);
        gradientAnisotropicDiffusionImageFilter.SetConductanceParameter(3.0);

        castImageFilterIF3ISS3.Update();


        for (int i = 0; i < step; i++) {
            itkBinaryImageToShapeLabelMapFilterIUC3LM3 labelMapFilter = new itkBinaryImageToShapeLabelMapFilterIUC3LM3();

            short threshold = (short) (minThreshold + gap * (step - i));

            itkImageUC3 noduleThresholdImage = ImageProcessingUtils.thresholdImageL(castImageFilterIF3ISS3.GetOutput(), threshold);

            System.out.println("T: " + threshold);

            labelMapFilter.SetInput(noduleThresholdImage);

            labelMapFilter.ComputeFeretDiameterOn();
            labelMapFilter.ComputePerimeterOn();

            labelMapFilter.Update();

            ImageProcessingUtils.toc();

            long labels = labelMapFilter.GetOutput().GetNumberOfLabelObjects();
            for (long l = 1; l <= labels; l++) {
                itkStatisticsLabelObjectUL3 labelObject = labelMapFilter.GetOutput().GetLabelObject(l);

                double volume = labelObject.GetPhysicalSize();
                double pixels = labelObject.Size();
                itkVectorD3 principalMoments = labelObject.GetPrincipalMoments();
                double roundness = labelObject.GetRoundness();
                //double elongation = labelObject.GetElongation();
                double elongation = Math.abs(principalMoments.GetElement(2) / principalMoments.GetElement(1));
                double feretDiameter = labelObject.GetFeretDiameter();

                // TODO this filter is not accurate
                //System.out.println(feretDiameter +" "+ roundness);
                //System.out.println(labelObject);
                if (feretDiameter < 3 || volume < Math.pow(1.5, 3) * Math.PI * 4 / 3) { // small object
                    continue;
                }
                if (feretDiameter > 30 || volume > Math.pow(15, 3) * Math.PI * 4 / 3) { // huge object
                    if (roundness < 0.8 || roundness > 1.2 || elongation > 4) { // vessel
                        System.out.println("R: " + roundness + ", E: " + elongation);
                        labelObject.SetLabel(400);
                        new_vlabel++;
                        vesselMap_.AddLabelObject(labelObject);
                    }
                    continue;
                }
                if (elongation > 4) { // vessel - elongated object
                    labelObject.SetLabel(300);
                    new_vlabel++;
                    vesselMap_.AddLabelObject(labelObject);
                    System.out.println("Elongation:" + elongation);

                    continue;
                }
                if (roundness < 0.8 || roundness > 1.2) {
                    continue;
                }
                // vessel overlap check
                if (new_vlabel > 0) {
                    int overlap = 0;
                    for (int p = 0; p < pixels; p++) {
                        if (vesselMap_.GetPixel(labelObject.GetIndex(p)) > 0)
                            overlap++;
                    }
                    double ratio = overlap / pixels;
                    System.out.println("Overlap:" + overlap + "/" + pixels + "=" + ratio);
                    if (ratio > 0.3) {
                        labelObject.SetLabel(200);
                        new_vlabel++;
                        vesselMap_.AddLabelObject(labelObject);
                        continue;
                    }
                }

                labelObject.SetLabel(100);
                new_label++;
                noduleCandidates_.AddLabelObject(labelObject);
            }
            System.out.println("Objects " + labels + " " + noduleCandidates_.GetNumberOfLabelObjects());
            System.out.println(new_label + ", " + new_vlabel);
            ImageProcessingUtils.toc();
        }

        {
            itkLabelMapToBinaryImageFilterLM3IUC3 labelMapToBinaryImageFilter = new itkLabelMapToBinaryImageFilterLM3IUC3();
            itkBinaryImageToLabelMapFilterIUC3LM3 binaryImageToLabelMapFilter = new itkBinaryImageToLabelMapFilterIUC3LM3();

            labelMapToBinaryImageFilter.SetInput(noduleCandidates_);
            binaryImageToLabelMapFilter.SetInput(labelMapToBinaryImageFilter.GetOutput());
            binaryImageToLabelMapFilter.Update();
            noduleCandidatesMask_ = labelMapToBinaryImageFilter.GetOutput();
            noduleCandidates_ = binaryImageToLabelMapFilter.GetOutput();
        }

        {
            itkLabelMapToBinaryImageFilterLM3IUC3 labelMapToBinaryImageFilter = new itkLabelMapToBinaryImageFilterLM3IUC3();
            itkBinaryImageToLabelMapFilterIUC3LM3 binaryImageToLabelMapFilter = new itkBinaryImageToLabelMapFilterIUC3LM3();

            labelMapToBinaryImageFilter.SetInput(vesselMap_);
            binaryImageToLabelMapFilter.SetInput(labelMapToBinaryImageFilter.GetOutput());
            binaryImageToLabelMapFilter.Update();
            vesselMask_ = labelMapToBinaryImageFilter.GetOutput();
            vesselMap_ = binaryImageToLabelMapFilter.GetOutput();
        }
        ImageProcessingUtils.toc();


        //ImageProcessingUtils.writeLabelMapOverlay(vesselMap, lungSegImage, "/Users/taznux/desktop/vessel.mha");
    }

    public itkImageSS3 getNoduleCandidatesLabel() {
        return noduleCandidatesLabel_;
    }
}
