import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:visibility_detector/visibility_detector.dart';

import 'CameraKitController.dart';

enum CameraFlashMode { on, off, auto }
enum ScaleTypeMode { fit, fill }

class CameraKitView extends StatefulWidget {
  Function onBarcodeRead;
  Function onTakePicture;
  Function onPermissionDenied;

  ScaleTypeMode scaleType;
  bool hasBarcodeReader;
  CameraFlashMode previewFlashMode;

  _BarcodeScannerViewState viewState;

  CameraKitController cameraKitController;

  CameraKitView(
      {Key key,
      this.hasBarcodeReader = false,
      this.scaleType = ScaleTypeMode.fill,
      this.onBarcodeRead,
      this.onTakePicture,
      this.previewFlashMode = CameraFlashMode.auto,
      this.cameraKitController,
      this.onPermissionDenied})
      : super(key: key) {}

  dispose() {
    viewState.disposeView();
  }

  @override
  State<StatefulWidget> createState() {
    cameraKitController.setView(this);
    viewState = _BarcodeScannerViewState();
    return viewState;
  }
}

class _BarcodeScannerViewState extends State<CameraKitView>
    with WidgetsBindingObserver {
  NativeCameraKitController controller;
  VisibilityDetector visibilityDetector;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (defaultTargetPlatform == TargetPlatform.android) {
      visibilityDetector = VisibilityDetector(
          key: Key('visible-camerakit-key-1'),
          onVisibilityChanged: (visibilityInfo) {
            if (visibilityInfo.visibleFraction == 0)
              controller.setCameraVisible(false);
            else
              controller.setCameraVisible(true);
          },
          child: AndroidView(
            viewType: 'plugins/camera_kit',
            onPlatformViewCreated: _onPlatformViewCreated,
          ));
    } else {
      visibilityDetector = VisibilityDetector(
          key: Key('visible-camerakit-key-1'),
          onVisibilityChanged: (visibilityInfo) {
            if (visibilityInfo.visibleFraction == 0)
              controller.setCameraVisible(false);
            else
              controller.setCameraVisible(true);
          },
          child: UiKitView(
            viewType: 'plugins/camera_kit',
            onPlatformViewCreated: _onPlatformViewCreated,
          ));
    }
  }

  @override
  Widget build(BuildContext context) {
    return visibilityDetector;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        print("Flutter Life Cycle: resumed");
        if (controller != null) controller.resumeCamera();
        break;
      case AppLifecycleState.inactive:
        print("Flutter Life Cycle: inactive");
        if (Platform.isIOS) {
          controller.pauseCamera();
        }
        break;
      case AppLifecycleState.paused:
        print("Flutter Life Cycle: paused");
        controller.pauseCamera();
        break;
      default:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  void _onPlatformViewCreated(int id) {
    this.controller = new NativeCameraKitController._(id, context, widget);
    this.controller.initCamera();
    widget.cameraKitController.isInitial = true;
  }

  void disposeView() {
    controller.dispose();
  }
}

class NativeCameraKitController {
  BuildContext context;
  CameraKitView widget;

  NativeCameraKitController._(int id, this.context, this.widget)
      : _channel = new MethodChannel('plugins/camera_kit_' + id.toString());

  final MethodChannel _channel;

  Future<dynamic> nativeMethodCallHandler(MethodCall methodCall) async {
    print("Flutter barcode read: " + methodCall.method);
    if (methodCall.method == "onBarcodeRead") {
      if (widget.onBarcodeRead != null)
        widget.onBarcodeRead(methodCall.arguments);
    } else if (methodCall.method == "onTakePicture") {
      if (widget.onTakePicture != null)
        widget.onTakePicture(methodCall.arguments);
    }

    return null;
  }

  bool _getScaleTypeMode(ScaleTypeMode scaleType) {
    if (scaleType == ScaleTypeMode.fill)
      return true;
    else
      return false;
  }

  String _getCharFlashMode(CameraFlashMode cameraFlashMode) {
    String flashMode;
    switch (cameraFlashMode) {
      case CameraFlashMode.auto:
        flashMode = "A";
        break;
      case CameraFlashMode.on:
        flashMode = "O";
        break;
      case CameraFlashMode.off:
        flashMode = "F";
        break;
    }
    return flashMode;
  }

  void initCamera() async {
    _channel.setMethodCallHandler(nativeMethodCallHandler);
    _channel.invokeMethod('requestPermission').then((value) {
      if (value) {
        _channel.invokeMethod('initCamera', {
          "hasBarcodeReader": widget.hasBarcodeReader,
          "flashMode": _getCharFlashMode(widget.previewFlashMode),
          "isFillScale": _getScaleTypeMode(widget.scaleType)
        });
      } else {
        widget.onPermissionDenied();
      }
    });
  }

  Future<void> resumeCamera() async {
    return _channel.invokeMethod('resumeCamera');
  }

  Future<void> pauseCamera() async {
    return _channel.invokeMethod('pauseCamera');
  }

  Future<void> closeCamera() {
    return _channel.invokeMethod('closeCamera');
  }

  Future<String> takePicture() async {
    return _channel.invokeMethod('takePicture', null);
  }

  Future<void> changeFlashMode(CameraFlashMode captureFlashMode) {
    return _channel.invokeMethod(
        'changeFlashMode', {"flashMode": _getCharFlashMode(captureFlashMode)});
  }

  Future<void> dispose() {
    return _channel.invokeMethod('dispose', "");
  }

  Future<void> setCameraVisible(bool isCameraVisible) {
    return _channel
        .invokeMethod('setCameraVisible', {"isCameraVisible": isCameraVisible});
  }
}
