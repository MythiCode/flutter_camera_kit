package com.MythiCode.camerakit;

import android.media.Image;
import android.media.ImageReader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;

public class BarcodeDetector {

    private static ImageReader imageReader;
    private static boolean isBusy = false;

    public static void setImageReader(ImageReader imageReader) {
        BarcodeDetector.imageReader = imageReader;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static void detectImage(final ImageReader imageReader, BarcodeScanner scanner
            , final Image inputImage, final FlutterMethodListener flutterMethodListener, int firebaseOrientation) {

        if (!isBusy) {
            if (imageReader == BarcodeDetector.imageReader && inputImage != null && scanner != null) {
                isBusy = true;
                scanner.process(InputImage.fromMediaImage(inputImage, firebaseOrientation))
                        .addOnSuccessListener(new OnSuccessListener<List<com.google.mlkit.vision.barcode.Barcode>>() {
                            @Override
                            public void onSuccess(List<com.google.mlkit.vision.barcode.Barcode> barcodes) {
                                if (imageReader == BarcodeDetector.imageReader) {
                                    if (barcodes.size() > 0) {
                                        for (com.google.mlkit.vision.barcode.Barcode barcode : barcodes
                                        ) {
                                            flutterMethodListener.onBarcodeRead(barcode.getRawValue());

                                        }
                                    }
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        System.out.println("barcode read failed: " + e.getMessage());
                    }
                })
                        .addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Barcode>> task) {
                                isBusy = false;
                                inputImage.close();
                            }
                        });
            } else {
                inputImage.close();
            }
        } else {
            inputImage.close();
        }

    }


}
