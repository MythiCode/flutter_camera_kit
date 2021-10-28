package com.MythiCode.camerakit;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.platform.PlatformViewRegistry;

import static com.google.mlkit.vision.barcode.Barcode.FORMAT_ALL_FORMATS;

/**
 * CamerakitPlugin
 */
public class CamerakitPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
//  private MethodChannel channel;
    private PlatformViewRegistry registery;
    private DartExecutor dartExecuter;
    private MethodChannel channel;
    private FlutterPluginBinding flutterPluginBinding;


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "plugins/camera_kit_direct");
        this.flutterPluginBinding = flutterPluginBinding;
        channel.setMethodCallHandler(this);
        registery = flutterPluginBinding.getFlutterEngine().getPlatformViewsController().getRegistry();
        dartExecuter = flutterPluginBinding.getFlutterEngine().getDartExecutor();

//    System.out.println("onAttachedToEngine");


    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    public static void registerWith(Registrar registrar) {
//    final MethodChannel channel = new MethodChannel(registrar.messenger(), "camerakit");
//    channel.setMethodCallHandler(new CamerakitPlugin());

//    if (registrar.activity() != null) {
//      registrar
//              .platformViewRegistry()
//              .registerViewFactory(
//                      "plugins/camera_kit"
//                      , new CameraKitFactory(registrar));
//    }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
        if (call.method.equals("processImage")) {
            InputImage image =
                    null;
            try {
                String path = call.argument("path").toString();
                image = InputImage.fromFilePath(flutterPluginBinding.getApplicationContext(), Uri.fromFile(new File(path)));

                BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                FORMAT_ALL_FORMATS
                        )
                        .build();

                BarcodeScanner scanner = BarcodeScanning.getClient(options);

                scanner.process(image)
                        .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                            @Override
                            public void onSuccess(List<Barcode> barcodes) {
                                if (barcodes.size() > 0) {
                                    for (Barcode barcode : barcodes
                                    ) {
                                        result.success(barcode.getRawValue());
                                    }
                                }
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                String error = "Error in reading barcode: " + e.getMessage();
                                System.out.println(error);
                                result.error("101",error, null);
                            }
                        })
//                        .addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
//                            @Override
//                            public void onComplete(@NonNull Task<List<Barcode>> task) {
//
//                            }
//                        })
                ;
            } catch (IOException e) {
                e.printStackTrace();
                result.error("102",e.getMessage(), null);

            }


        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {

    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        System.out.println("onAttachedToActivity");
        if (binding.getActivity() != null) {
            registery
                    .registerViewFactory(
                            "plugins/camera_kit"
                            , new CameraKitFactory(binding, dartExecuter));
        }
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }
}
