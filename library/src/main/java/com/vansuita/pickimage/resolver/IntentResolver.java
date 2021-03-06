package com.vansuita.pickimage.resolver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;

import com.vansuita.pickimage.bundle.PickSetup;
import com.vansuita.pickimage.dialog.PickImageDialog;
import com.vansuita.pickimage.enums.EPickType;
import com.vansuita.pickimage.keep.Keep;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jrvansuita build 07/02/17.
 */

public class IntentResolver {

    public static final int REQUESTER = 99;
    private Activity activity;

    private Intent galleryIntent;
    private Intent cameraIntent;


    public IntentResolver(Activity activity) {
        this.activity = activity;
    }

    private Intent loadSystemPackages(Intent intent){
        List<ResolveInfo> resInfo = activity.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY);

        if (!resInfo.isEmpty()){
            String packageName = resInfo.get(0).activityInfo.packageName;
            intent.setPackage(packageName);
        }

        return intent;
    }

    public boolean isCamerasAvailable() {
        String feature = PackageManager.FEATURE_CAMERA;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            feature = PackageManager.FEATURE_CAMERA_ANY;
        }

        return activity.getPackageManager().hasSystemFeature(feature);
    }

    private Intent getCameraIntent() {
        if (cameraIntent == null) {
            cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUriForProvider());

            applyProviderPermission();
        }

        return cameraIntent;
    }

    public void launchCamera(Fragment listener) {
        if (getCameraIntent().resolveActivity(activity.getPackageManager()) != null) {
            listener.startActivityForResult(loadSystemPackages(getCameraIntent()), REQUESTER);
        }
    }

    /**
     * Granting permissions to write and read for available cameras to file provider.
     */
    private void applyProviderPermission() {
        List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            activity.grantUriPermission(packageName, cameraUriForProvider(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private File cameraFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), PickImageDialog.class.getSimpleName());
        dir.mkdirs();

        return new File(dir, "current.jpg");
    }

    public Uri cameraUri() {
        return Uri.fromFile(cameraFile());
    }

    private String getAuthority() {
        return activity.getApplication().getPackageName() + ".com.vansuita.pickimage.provider";
    }

    private Uri cameraUriForProvider() {
        try {
            return FileProvider.getUriForFile(activity, getAuthority(), cameraFile());
        } catch (Exception e) {
            throw new Error("FileProvider not declared or has wrong authority. (Must be ${applicationId}.com.vansuita.pickimage.provider, check AndroidManifest.xml)");
        }
    }


    private Intent getGalleryIntent() {
        if (galleryIntent == null) {
            galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryIntent.setType("image/*");
        }

        return galleryIntent;
    }

    public void launchGallery(Fragment listener) {
        listener.startActivityForResult(loadSystemPackages(getGalleryIntent()), REQUESTER);
    }

    public void launchSystemChooser(PickSetup setup, Fragment listener) {
        Intent chooserIntent;
        List<Intent> intentList = new ArrayList<>();

        boolean showCamera = EPickType.CAMERA.inside(setup.getPickTypes());
        boolean showGallery = EPickType.GALLERY.inside(setup.getPickTypes());

        if (showGallery)
            intentList.add(getGalleryIntent());

        if (showCamera && isCamerasAvailable() && !wasCameraPermissionDeniedForever())
            intentList.add(getCameraIntent());

        if (intentList.size() > 0) {
            chooserIntent = Intent.createChooser(intentList.remove(intentList.size() - 1), setup.getTitle());
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toArray(new Parcelable[]{}));
            listener.startActivityForResult(chooserIntent, REQUESTER);
        }
    }

    private String[] getMandatoryCameraPermissions() {
        return new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    public boolean wasCameraPermissionDeniedForever() {
        if (Keep.with(activity).neverAskedForPermissionYet())
            return false;

        for (String permission : getMandatoryCameraPermissions()) {
            if (((ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED)
                    && (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)))) {
                return true;
            }
        }

        return false;
    }

    /**
     * resquest permission to use camera and write files
     */
    public boolean requestCameraPermissions(Fragment listener) {
        List<String> list = new ArrayList<>();

        for (String permission : getMandatoryCameraPermissions())
            if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED)
                list.add(permission);


        if (list.isEmpty())
            return true;


        listener.requestPermissions(list.toArray(new String[list.size()]), REQUESTER);
        return false;
    }


    public boolean fromCamera(Intent data) {
        return (data == null || data.getData() == null || data.getData().toString().contains(cameraFile().toString()));
    }

    public Activity getActivity() {
        return activity;
    }
}
