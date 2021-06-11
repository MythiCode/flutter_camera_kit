Use Prerelease version to support nullsafety.
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


## iOS
Add `io.flutter.embedded_views_preview` in info.plist with value `YES`.
Add `Privacy - Camera Usage Description` in info.plist.

# Usage

For use plugin, You shoude an instance of `CameraKitController` then initial `CameraKitView` passing `CameraKitController` instance to it.
```
   cameraKitController = CameraKitController();
    cameraKitView = CameraKitView(
      cameraKitController: cameraKitController,
      hasBarcodeReader: true,
      barcodeFormat: BarcodeFormats.FORMAT_ALL_FORMATS
      scaleType: ScaleTypeMode.fill,
      previewFlashMode: CameraFlashMode.auto,
      androidCameraMode: AndroidCameraMode.API_X,
      cameraSelector: CameraSelector.back
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

`barcodeFormat`:
Set barcode format from available values, default value is FORMAT_ALL_FORMATS.

`scaleType`:
There are 2 modes `ScaleTypeMode.fill` and `ScaleTypeMode.fit` for this parameter.
If you want camera preview fill your widget area, use `fill` mode. In this mode, camera preview may be croped for filling widget area.
If you want camera preview to show entire lens preview, use `fit` mode. In this mode, camera preview may be shows blank areas.

`previewFlashMode`:
This parameter accepts 3 values, `CameraFlashMode.auto`, `CameraFlashMode.on` and `CameraFlashMode.off`. For changing value after initial use `changeFlashMode` method in controller.

`androidCameraMode`:
**This parameter has been replaced with `useCamera2API`.**
This parameter accepts 3 values, `API_X`, `API_1`, `API_2`. Default value is `API_X`.
Some feature is available in each value.
`API_1` features: Taking picture
`API_2` features: Taking picture, Scaning barcode (Taking picture with flash has some issues, Auto flash in barcode scanning mode works in some phones.)
`API_X` features: Taking picture, Scaning barcode (Auto flash in barcode scanning mode doesn't work.)

`cameraSelector`:
Set front and back camera with this parameter.
This parameter accepts 3 values, `back` and `front`

`onPermissionDenied`:
After android and iOS user deny run time permission, this method is called.

`onBarcodeRead`:
In barcodeReader mode, while camera preview detect barcodes, This method is called.

## Controller methods
**Take Picture**
You can take picture with this method. Unlike `API_2`, in `API_X`, you can take picture in scaning barcode mode.
You can pass your custom path for save image. You can pass nothing and the image will be saved in default path.

```
  String path = await cameraKitController.takePicture({Your custom path});
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

