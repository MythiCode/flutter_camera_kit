# Flutter Camera Kit



Flutter camera kit uses native android and ios APIs for taking picture and scanning barcode with controlable flash.This plugin uses Camera2 API in android and AVFoundation in iOS for taking picture and  MLFirebase library for scanning barcode.

Main features:
  - Easy implementation with controller
  - Automatic permission handling (no need for 3rd-party libraries)
  - Automatic camera resource handling for battery usage and avoid any conflict with another camera based applications.(you can also manage manually pause and resume camera)
  - `fill` and `fit` mode for camera preview frame.
  - Optimize memory usage in taking picture
  - `auto`, `on` and `off` flash mode while taking picture and scan barcode.


# Installation

## Firebase
For barcode reading, You should install firebase in your project. accordig to steps 1, 2, and 3 from [Firebase's Flutter instructions](https://firebase.google.com/docs/flutter/setup).
You don't need to add any code in your project after installation.

## iOS
Add `io.flutter.embedded_views_preview` in info.plist with value `YES`.
Add `Privacy - Camera Usage Description` in info.plist.

# Usage
##Sample Usage
For use plugin, You shoude an instance of `CameraKitController` then initial `CameraKitView` passing `CameraKitController` instance to it.
```
   cameraKitController = CameraKitController();
    cameraKitView = CameraKitView(
      cameraKitController: cameraKitController,
      hasBarcodeReader: true,
      scaleType: ScaleTypeMode.fill,
      previewFlashMode: CameraFlashMode.auto
      onPermissionDenied: () {
          print("Camera permission is denied.");
          //ToDo on permission denied by user
      },
      onBarcodeRead: (code) {
        print("Barcode is read: " + code);
        //ToDo on barcode read
      },
    );
```
## Cunstructor parameters
`hasBarcodeReader`:
True means scan barcode mode and false means take picture mode
Because of performance reasons, you can't use barcode reader mode and take picture mode simultaneously.
`scaleType`:
There are 2 modes `ScaleTypeMode.fill` and `ScaleTypeMode.fit` for this parameter.
If you want camera preview fill your widget area, use `fill` mode. In this mode, camera preview may be croped for filling widget area.
If you want camera preview to show entire lens preview, use `fit` mode. In this mode, camera preview may be shows blank areas.
`previewFlashMode`:
This parameter accepts 3 values. `CameraFlashMode.auto`, `CameraFlashMode.on` and `CameraFlashMode.off`. For changing value after initial use `changeFlashMode` method in controller.
`onPermissionDenied`:
After android and iOS user deny run time permission, this method is called.
`onBarcodeRead`:
In barcodeReader mode, while camera preview detect barcodes, This method is called.

## Controller methods
**Take Picture**
For taking picture pass `hasBarcodeReader`'s value `false` and then use controller for taking picture.
```
  String path = await cameraKitController.takePicture();
```
**Change Flash Mode**
For changing flash mode, don't change cunstructor parameter. Use `changeFlashMode` method in controller. Pass `CameraFlashMode` enum to this method.
```
    cameraKitController.changeFlashMode(CameraFlashMode.on);
```
**Pause and Resume camera**
This plugin automatically manage pause and resume camera based on android, iOS life cycle and widget visibility, also you can call with your controller when ever you need.
```
    cameraKitController.pauseCamera();
    cameraKitController.resumeCamera();
```
 # Notes
 This project is teset on iPhone6, samsung galaxy S7, samsung J7, samsung note 10, samsung s10, iPhone pro max.
 Auto flash for capture has a bug in samsung galaxy S5 and there is no answer for this issue in git and stackoverflow.
 Auto flash for camera preview (scanning barcode mode) dosen't work for samsung J7
