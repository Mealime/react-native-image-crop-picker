//
//  ImageManager.m
//
//  Created by Ivan Pusic on 5/4/16.
//  Copyright © 2016 Facebook. All rights reserved.
//

#import "ImageCropPicker.h"

#define ERROR_CANNOT_SAVE_IMAGE_KEY @"cannot_save_image"
#define ERROR_CANNOT_SAVE_IMAGE_MSG @"Cannot save image. Unable to write to tmp location."

@implementation ImageCropPicker

RCT_EXPORT_MODULE();


- (instancetype)init
{
    if (self = [super init]) {
        self.defaultOptions = @{
                                @"multiple": @NO,
                                @"cropping": @NO,
                                @"maxFiles": @5,
                                @"width": @200,
                                @"height": @200
                                };
    }
    
    return self;
}

RCT_EXPORT_METHOD(openPicker:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    self.resolve = resolve;
    self.reject = reject;
    self.options = [NSMutableDictionary dictionaryWithDictionary:self.defaultOptions];
    for (NSString *key in options.keyEnumerator) {
        [self.options setValue:options[key] forKey:key];
    }
    
    [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {
        dispatch_async(dispatch_get_main_queue(), ^{
            // init picker
            QBImagePickerController *imagePickerController =
            [QBImagePickerController new];
            imagePickerController.delegate = self;
            imagePickerController.allowsMultipleSelection = [[self.options objectForKey:@"multiple"] boolValue];
            imagePickerController.maximumNumberOfSelection = [[self.options objectForKey:@"maxFiles"] intValue];
            imagePickerController.showsNumberOfSelectedAssets = YES;
            imagePickerController.mediaType = QBImagePickerMediaTypeImage;
            
            UIViewController *root = [[[[UIApplication sharedApplication] delegate]
                                       window] rootViewController];
            [root presentViewController:imagePickerController
                               animated:YES
                             completion:NULL];
        });
    }];
}

- (void)qb_imagePickerController:
(QBImagePickerController *)imagePickerController
          didFinishPickingAssets:(NSArray *)assets {
    
    PHImageManager *manager = [PHImageManager defaultManager];
    
    if ([[[self options] objectForKey:@"multiple"] boolValue]) {
        NSMutableArray *images = [[NSMutableArray alloc] init];
        PHImageRequestOptions* options = [[PHImageRequestOptions alloc] init];
        options.synchronous = YES;
        
        for (PHAsset *asset in assets) {
            [manager
             requestImageDataForAsset:asset
             options:options
             resultHandler:^(NSData *imageData, NSString *dataUTI, UIImageOrientation orientation, NSDictionary *info) {
                 NSString *filePath = [self persistFile:imageData];
                 if (filePath == nil) {
                     self.reject(ERROR_CANNOT_SAVE_IMAGE_KEY, ERROR_CANNOT_SAVE_IMAGE_MSG, nil);
                     return;
                 }
                 
                 [images addObject:@{
                                     @"path": filePath,
                                     @"width": @(asset.pixelWidth),
                                     @"height": @(asset.pixelHeight)
                                     }];
             }];
        }
        
        self.resolve(images);
        [imagePickerController dismissViewControllerAnimated:YES completion:nil];
    } else {
        PHAsset *asset = [assets objectAtIndex:0];
        
        [manager
         requestImageDataForAsset:asset
         options:nil
         resultHandler:^(NSData *imageData, NSString *dataUTI,
                         UIImageOrientation orientation,
                         NSDictionary *info) {
             
             if ([[[self options] objectForKey:@"cropping"] boolValue]) {
                 UIImage *image = [UIImage imageWithData:imageData];
                 RSKImageCropViewController *imageCropVC = [[RSKImageCropViewController alloc] initWithImage:image cropMode:RSKImageCropModeCustom];
                 
                 imageCropVC.avoidEmptySpaceAroundImage = YES;
                 imageCropVC.dataSource = self;
                 imageCropVC.delegate = self;
                 
                 UIViewController *root = [[[[UIApplication sharedApplication] delegate] window] rootViewController];
                 [imagePickerController dismissViewControllerAnimated:YES completion:nil];
                 [root presentViewController:imageCropVC animated:YES completion:nil];
             } else {
                 NSString *filePath = [self persistFile:imageData];
                 if (filePath == nil) {
                     self.reject(ERROR_CANNOT_SAVE_IMAGE_KEY, ERROR_CANNOT_SAVE_IMAGE_MSG, nil);
                     return;
                 }
                 
                 self.resolve(@{
                                @"path": filePath,
                                @"width": @(asset.pixelWidth),
                                @"height": @(asset.pixelHeight)
                                });
                 [imagePickerController dismissViewControllerAnimated:YES completion:nil];
             }
         }];
    }
}

- (void)qb_imagePickerControllerDidCancel:(QBImagePickerController *)imagePickerController {
    [imagePickerController dismissViewControllerAnimated:YES completion:nil];
}

#pragma mark - CustomCropModeDelegates

// Returns a custom rect for the mask.
- (CGRect)imageCropViewControllerCustomMaskRect:
(RSKImageCropViewController *)controller {
    CGSize maskSize = CGSizeMake(
                                 [[self.options objectForKey:@"width"] intValue],
                                 [[self.options objectForKey:@"height"] intValue]);
    
    CGFloat viewWidth = CGRectGetWidth(controller.view.frame);
    CGFloat viewHeight = CGRectGetHeight(controller.view.frame);
    
    CGRect maskRect = CGRectMake((viewWidth - maskSize.width) * 0.5f,
                                 (viewHeight - maskSize.height) * 0.5f,
                                 maskSize.width, maskSize.height);
    
    return maskRect;
}

// if provided width or height is bigger than screen w/h,
// then we should scale draw area
- (CGRect) scaleRect:(RSKImageCropViewController *)controller {
    CGRect rect = controller.maskRect;
    CGFloat viewWidth = CGRectGetWidth(controller.view.frame);
    CGFloat viewHeight = CGRectGetHeight(controller.view.frame);
    
    if (rect.size.width > viewWidth) {
        float scaleFactor = viewWidth / rect.size.width;
        rect.size.width *= scaleFactor;
        rect.size.height *= scaleFactor;
        rect.origin.x = 0;
        rect.origin.y = viewHeight / 2 * 0.5f;
    } else if (rect.size.height > viewHeight) {
        float scaleFactor = viewHeight / rect.size.height;
        rect.size.width *= scaleFactor;
        rect.size.height *= scaleFactor;
        rect.origin.x = viewWidth / 2 * 0.5f;
        rect.origin.y = 0;
    }
    
    return rect;
}

// Returns a custom path for the mask.
- (UIBezierPath *)imageCropViewControllerCustomMaskPath:
(RSKImageCropViewController *)controller {
    CGRect rect = [self scaleRect:controller];
    UIBezierPath *path = [UIBezierPath bezierPathWithRoundedRect:rect
                                               byRoundingCorners:UIRectCornerAllCorners
                                                     cornerRadii:CGSizeMake(0, 0)];
    return path;
}

// Returns a custom rect in which the image can be moved.
- (CGRect)imageCropViewControllerCustomMovementRect:
(RSKImageCropViewController *)controller {
    return [self scaleRect:controller];
}

#pragma mark - CropFinishDelegate

// Crop image has been canceled.
- (void)imageCropViewControllerDidCancelCrop:
(RSKImageCropViewController *)controller {
    [controller dismissViewControllerAnimated:YES completion:nil];
}

// The original image has been cropped.
- (void)imageCropViewController:(RSKImageCropViewController *)controller
                   didCropImage:(UIImage *)croppedImage
                  usingCropRect:(CGRect)cropRect {
    
    // we have correct rect, but not correct dimensions
    // so resize image
    CGSize resizedImageSize = CGSizeMake([[[self options] objectForKey:@"width"] intValue], [[[self options] objectForKey:@"height"] intValue]);
    UIImage *resizedImage = [croppedImage resizedImageToFitInSize:resizedImageSize scaleIfSmaller:YES];
    NSData *data = UIImageJPEGRepresentation(resizedImage, 1);
    
    NSString *filePath = [self persistFile:data];
    if (filePath == nil) {
        self.reject(ERROR_CANNOT_SAVE_IMAGE_KEY, ERROR_CANNOT_SAVE_IMAGE_MSG, nil);
        return;
    }
    
    NSDictionary *image = @{
                            @"path": filePath,
                            @"width": @(resizedImage.size.width),
                            @"height": @(resizedImage.size.height)
                            };
    
    self.resolve(image);
    [controller dismissViewControllerAnimated:YES completion:nil];
}

// at the moment it is not possible to upload image by reading PHAsset
// we are saving image and saving it to the tmp location where we are allowed to access image later
- (NSString*) persistFile:(NSData*)data {
    // create temp file
    NSString *filePath = [NSTemporaryDirectory() stringByAppendingString:[[NSUUID UUID] UUIDString]];
    filePath = [filePath stringByAppendingString:@".jpg"];
    
    // save cropped file
    BOOL status = [data writeToFile:filePath atomically:YES];
    if (!status) {
        return nil;
    }
    
    return filePath;
}

// The original image has been cropped. Additionally provides a rotation angle
// used to produce image.
- (void)imageCropViewController:(RSKImageCropViewController *)controller
                   didCropImage:(UIImage *)croppedImage
                  usingCropRect:(CGRect)cropRect
                  rotationAngle:(CGFloat)rotationAngle {
    [self imageCropViewController:controller didCropImage:croppedImage usingCropRect:cropRect];
}

@end
