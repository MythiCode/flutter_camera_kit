import 'dart:async';

import 'package:camerakit/CameraKitController.dart';
import 'package:camerakit/CameraKitView.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String? _platformVersion = 'Unknown';

  CameraFlashMode _flashMode = CameraFlashMode.on;
  CameraKitController? cameraKitController;

  @override
  void initState() {
    super.initState();
    cameraKitController = CameraKitController();
    print("cameraKitController" + cameraKitController.toString());
    // cameraKitView = CameraKitView(
    //   hasBarcodeReader: true,
    //   onBarcodeRead: (barcode) {
    //     print("Flutter read barcode: " + barcode);
    //   },
    //   previewFlashMode: CameraFlashMode.auto,
    //   cameraKitController: cameraKitController,
    // );
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String? platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
//    try {
//      platformVersion = await Camerakit.platformVersion;
//    } on PlatformException {
//      platformVersion = 'Failed to get platform version.';
//    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Container(
          height: 2000,
          child: Column(
            children: <Widget>[
              Expanded(
                child:Text("salam")
              //     child: CameraKitView(
              //   hasBarcodeReader: false,
              //   barcodeFormat: BarcodeFormats.FORMAT_ALL_FORMATS,
              //   scaleType: ScaleTypeMode.fill,
              //   onBarcodeRead: (barcode) {
              //     print("Flutter read barcode: " + barcode);
              //   },
              //   previewFlashMode: CameraFlashMode.auto,
              //   cameraKitController: cameraKitController,
              //   androidCameraMode: AndroidCameraMode.API_X,
              //   cameraSelector: CameraSelector.back,
              // )
              ),
//              Container(height: 250),
              Row(
                children: <Widget>[
                  RaisedButton(
                    child: Text("Flash OFF"),
                    onPressed: () {
                      setState(() {
                        cameraKitController!
                            .changeFlashMode(CameraFlashMode.off);
                        _platformVersion = "bbasda";
                      });
                    },
                  ),
                  RaisedButton(
                    child: Text("Capture"),
                    onPressed: () {
                      cameraKitController!.takePicture().then((value) =>
                          print("flutter take pictre result: " + value!));
                    },
                  ),
                  RaisedButton(
                    child: Text("Flash On"),
                    onPressed: () {
                      setState(() {
                        cameraKitController!.changeFlashMode(CameraFlashMode.on);
                        _platformVersion = "bbasda";
                      });
                    },
                  ),
                ],
              ),
              Builder(
                builder: (context) => RaisedButton(
                  child: Text("GO"),
                  onPressed: () async {
                    PickedFile? file = await ImagePicker.platform.pickImage(source: ImageSource.camera);
                    String path = file!.path;
                    final res = await CameraKitController().processImage(path);
                    // Navigator.push(
                    //     context,
                    //     MaterialPageRoute(
                    //         builder: (context) => Scaffold(
                    //               body: Text("Go is Here"),
                    //             )));

                  },
                ),
              )
            ],
          ),
        ),
      ),
    );
  }
}
