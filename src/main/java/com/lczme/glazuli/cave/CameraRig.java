package com.lczme.glazuli.cave;

import java.util.List;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/** Where a scene puts the camera: a viewpoint per kind of menu, the startup flight in and the dive while joining. */
interface CameraRig {
    Vector3f spotEye(CaveCamera.Spot spot);

    Vector3f spotLook(CaveCamera.Spot spot);

    /** Ends at the title spot's eye. */
    List<Vector3f> introPath();

    default float introSeconds() {
        return 3.4F;
    }

    /** Where the startup flight looks at first, turning onto the title view on the way; null to look along its way. */
    @Nullable
    default Vector3f introLook() {
        return null;
    }

    /** Starts at {@code start}. */
    List<Vector3f> divePath(Vector3f start);
}
