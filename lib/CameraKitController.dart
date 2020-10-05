import 'package:camerakit/CameraKitView.dart';

///This controller is used to control CameraKiView.dart
class CameraKitController {
  CameraKitView cameraKitView;

  ///pause camera while stop camera preview.
  ///Plugin manage automatically pause camera based android, iOS lifecycle and widget visibility
  pauseCamera() {
    cameraKitView.viewState.controller.pauseCamera();
  }

  ///Closing camera and dispose all resource
  closeCamera() {
    cameraKitView.viewState.controller.closeCamera();
  }

  ///resume camera while resume camera preview.
  ///Plugin manage automatically resume camera based android, iOS lifecycle and widget visibility
  resumeCamera() {
    cameraKitView.viewState.controller.resumeCamera();
  }

  ///Use this method for taking picture in take picture mode
  ///This method return path of image
  Future<String> takePicture() {
    return cameraKitView.viewState.controller.takePicture();
  }

  ///Change flash mode between auto, on and off
  changeFlashMode(CameraFlashMode captureFlashMode) {
    cameraKitView.viewState.controller.changeFlashMode(captureFlashMode);
  }

  ///Connect view to this controller
  void setView(CameraKitView cameraKitView) {
    this.cameraKitView = cameraKitView;
  }
}
