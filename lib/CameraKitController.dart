
import 'package:camerakit/CameraKitView.dart';
import 'package:flutter/widgets.dart';

class CameraKitController{
  CameraKitView cameraKitView;

  bool isInitial = false;

  pauseCamera() {
    cameraKitView.viewState.controller.pauseCamera();
  }

  closeCamera() {
    cameraKitView.viewState.controller.closeCamera();
  }

  resumeCamera() {
    cameraKitView.viewState.controller.resumeCamera();
  }


  Future<String> takePicture() {
    return cameraKitView.viewState.controller.takePicture();
  }

  changeFlashMode(CameraFlashMode captureFlashMode) {
    cameraKitView.viewState.controller.changeFlashMode(captureFlashMode);
  }

  void setView(CameraKitView cameraKitView) {
    this.cameraKitView = cameraKitView;
  }
}