import 'package:camerakit/CameraKitView.dart';
import 'package:flutter/services.dart';

///This controller is used to control CameraKiView.dart
class CameraKitController {
  late CameraKitView cameraKitView;

  ///pause camera while stop camera preview.
  ///Plugin manage automatically pause camera based android, iOS lifecycle and widget visibility
  pauseCamera() {
    cameraKitView.viewState.controller!.setCameraVisible(false);
  }

  ///Closing camera and dispose all resource
  closeCamera() {
    cameraKitView.viewState.controller!.closeCamera();
  }

  ///resume camera while resume camera preview.
  ///Plugin manage automatically resume camera based android, iOS lifecycle and widget visibility
  resumeCamera() {
    cameraKitView.viewState.controller!.setCameraVisible(true);
  }

  ///Use this method for taking picture in take picture mode
  ///This method return path of image
  Future<String?> takePicture({String path = ""}) {
    return cameraKitView.viewState.controller!.takePicture(path);
  }

  ///Change flash mode between auto, on and off
  changeFlashMode(CameraFlashMode captureFlashMode) {
    cameraKitView.viewState.controller!.changeFlashMode(captureFlashMode);
  }

  ///Connect view to this controller
  void setView(CameraKitView cameraKitView) {
    this.cameraKitView = cameraKitView;
  }

  Future<String?> processImage(String path){
    var _channel = new MethodChannel('plugins/camera_kit_direct' );

    return _channel.invokeMethod<String>(
        'processImage', {"path": path});

  }

}
