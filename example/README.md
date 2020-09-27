
# Example and usage
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
