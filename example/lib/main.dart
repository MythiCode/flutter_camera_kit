import 'dart:async';

import 'package:camerakit/CameraKitController.dart';
import 'package:camerakit/CameraKitView.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  CameraKitView cameraKitView;
  CameraFlashMode _flashMode = CameraFlashMode.on;
  CameraKitController cameraKitController;

  @override
  void initState() {
    super.initState();
    cameraKitController = CameraKitController();
    print("cameraKitController" + cameraKitController.toString());
    cameraKitView = CameraKitView(
      hasBarcodeReader: true,
      onBarcodeRead: (barcode) {
        print("Flutter read barcode: " + barcode);
      },
      previewFlashMode: CameraFlashMode.auto,
      cameraKitController: cameraKitController,
    );
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
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
                  child: CameraKitView(
                hasBarcodeReader: true,
                scaleType: ScaleTypeMode.fill,
                onBarcodeRead: (barcode) {
                  print("Flutter read barcode: " + barcode);
                },
                previewFlashMode: CameraFlashMode.auto,
                cameraKitController: cameraKitController,
              )),
//              Row(
//                children: <Widget>[
//                  RaisedButton(
//                    child: Text("Flash OFF"),
//                    onPressed: () {
//                      setState(() {
//                        cameraKitController
//                            .changeFlashMode(CameraFlashMode.off);
//                        _platformVersion = "bbasda";
//                      });
//                    },
//                  ),
//                  RaisedButton(
//                    child: Text("Capture"),
//                    onPressed: () {
//                      cameraKitController.takePicture().then((value) => print(value));
//                    },
//                  ),
//                  RaisedButton(
//                    child: Text("Flash On"),
//                    onPressed: () {
//                      setState(() {
//                        cameraKitController.changeFlashMode(CameraFlashMode.on);
//                        _platformVersion = "bbasda";
//                      });
//                    },
//                  ),
//                ],
//              ),
//              Builder(
//                builder: (context) => RaisedButton(
//                  child: Text("GO"),
//                  onPressed: () {
//                    Navigator.push(
//                        context,
//                        MaterialPageRoute(
//                            builder: (context) => Scaffold(
//                                  body: Text("Go is Here"),
//                                )));
//                  },
//                ),
//              )
            ],
          ),
        ),
      ),
    );
  }
}
