package xx.snapsort.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

// The few icons the app uses that are not in the core set, copied path for
// path from material-icons-extended 1.7.8: the whole library is thousands of
// icons, and shrinking it away took most of every release build's time.
// Declared as the library declares them, so a call site reads the same.

val Icons.Filled.Archive: ImageVector
    get() {
        if (_archive != null) return _archive!!
        _archive = materialIcon(name = "Filled.Archive") {
            materialPath {
                moveTo(20.54f, 5.23f)
                lineToRelative(-1.39f, -1.68f)
                curveTo(18.88f, 3.21f, 18.47f, 3.0f, 18.0f, 3.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.47f, 0.0f, -0.88f, 0.21f, -1.16f, 0.55f)
                lineTo(3.46f, 5.23f)
                curveTo(3.17f, 5.57f, 3.0f, 6.02f, 3.0f, 6.5f)
                verticalLineTo(19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.5f)
                curveToRelative(0.0f, -0.48f, -0.17f, -0.93f, -0.46f, -1.27f)
                close()
                moveTo(12.0f, 17.5f)
                lineTo(6.5f, 12.0f)
                horizontalLineTo(10.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.5f)
                lineTo(12.0f, 17.5f)
                close()
                moveTo(5.12f, 5.0f)
                lineToRelative(0.81f, -1.0f)
                horizontalLineToRelative(12.0f)
                lineToRelative(0.94f, 1.0f)
                horizontalLineTo(5.12f)
                close()
            }
        }
        return _archive!!
    }

private var _archive: ImageVector? = null

val Icons.Filled.Description: ImageVector
    get() {
        if (_description != null) return _description!!
        _description = materialIcon(name = "Filled.Description") {
            materialPath {
                moveTo(14.0f, 2.0f)
                lineTo(6.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(4.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
                lineTo(18.0f, 22.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(20.0f, 8.0f)
                lineToRelative(-6.0f, -6.0f)
                close()
                moveTo(16.0f, 18.0f)
                lineTo(8.0f, 18.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(16.0f, 14.0f)
                lineTo(8.0f, 14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(13.0f, 9.0f)
                lineTo(13.0f, 3.5f)
                lineTo(18.5f, 9.0f)
                lineTo(13.0f, 9.0f)
                close()
            }
        }
        return _description!!
    }

private var _description: ImageVector? = null

val Icons.Filled.Flight: ImageVector
    get() {
        if (_flight != null) return _flight!!
        _flight = materialIcon(name = "Filled.Flight") {
            materialPath {
                moveTo(21.0f, 16.0f)
                verticalLineToRelative(-2.0f)
                lineToRelative(-8.0f, -5.0f)
                verticalLineTo(3.5f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveTo(10.0f, 2.67f, 10.0f, 3.5f)
                verticalLineTo(9.0f)
                lineToRelative(-8.0f, 5.0f)
                verticalLineToRelative(2.0f)
                lineToRelative(8.0f, -2.5f)
                verticalLineTo(19.0f)
                lineToRelative(-2.0f, 1.5f)
                verticalLineTo(22.0f)
                lineToRelative(3.5f, -1.0f)
                lineToRelative(3.5f, 1.0f)
                verticalLineToRelative(-1.5f)
                lineTo(13.0f, 19.0f)
                verticalLineToRelative(-5.5f)
                lineToRelative(8.0f, 2.5f)
                close()
            }
        }
        return _flight!!
    }

private var _flight: ImageVector? = null

val Icons.Filled.Folder: ImageVector
    get() {
        if (_folder != null) return _folder!!
        _folder = materialIcon(name = "Filled.Folder") {
            materialPath {
                moveTo(10.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-2.0f, -2.0f)
                close()
            }
        }
        return _folder!!
    }

private var _folder: ImageVector? = null

val Icons.Filled.FolderOpen: ImageVector
    get() {
        if (_folderOpen != null) return _folderOpen!!
        _folderOpen = materialIcon(name = "Filled.FolderOpen") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-2.0f, -2.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                lineTo(4.0f, 8.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(10.0f)
                close()
            }
        }
        return _folderOpen!!
    }

private var _folderOpen: ImageVector? = null

val Icons.Filled.Mood: ImageVector
    get() {
        if (_mood != null) return _mood!!
        _mood = materialIcon(name = "Filled.Mood") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
                moveTo(15.5f, 11.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveTo(16.33f, 8.0f, 15.5f, 8.0f)
                reflectiveCurveTo(14.0f, 8.67f, 14.0f, 9.5f)
                reflectiveCurveToRelative(0.67f, 1.5f, 1.5f, 1.5f)
                close()
                moveTo(8.5f, 11.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveTo(9.33f, 8.0f, 8.5f, 8.0f)
                reflectiveCurveTo(7.0f, 8.67f, 7.0f, 9.5f)
                reflectiveCurveTo(7.67f, 11.0f, 8.5f, 11.0f)
                close()
                moveTo(12.0f, 17.5f)
                curveToRelative(2.33f, 0.0f, 4.31f, -1.46f, 5.11f, -3.5f)
                lineTo(6.89f, 14.0f)
                curveToRelative(0.8f, 2.04f, 2.78f, 3.5f, 5.11f, 3.5f)
                close()
            }
        }
        return _mood!!
    }

private var _mood: ImageVector? = null

val Icons.Filled.People: ImageVector
    get() {
        if (_people != null) return _people!!
        _people = materialIcon(name = "Filled.People") {
            materialPath {
                moveTo(16.0f, 11.0f)
                curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
                reflectiveCurveTo(17.66f, 5.0f, 16.0f, 5.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                close()
                moveTo(8.0f, 11.0f)
                curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
                reflectiveCurveTo(9.66f, 5.0f, 8.0f, 5.0f)
                curveTo(6.34f, 5.0f, 5.0f, 6.34f, 5.0f, 8.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                close()
                moveTo(8.0f, 13.0f)
                curveToRelative(-2.33f, 0.0f, -7.0f, 1.17f, -7.0f, 3.5f)
                lineTo(1.0f, 19.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.5f)
                curveToRelative(0.0f, -2.33f, -4.67f, -3.5f, -7.0f, -3.5f)
                close()
                moveTo(16.0f, 13.0f)
                curveToRelative(-0.29f, 0.0f, -0.62f, 0.02f, -0.97f, 0.05f)
                curveToRelative(1.16f, 0.84f, 1.97f, 1.97f, 1.97f, 3.45f)
                lineTo(17.0f, 19.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-2.5f)
                curveToRelative(0.0f, -2.33f, -4.67f, -3.5f, -7.0f, -3.5f)
                close()
            }
        }
        return _people!!
    }

private var _people: ImageVector? = null

val Icons.Filled.PlayCircle: ImageVector
    get() {
        if (_playCircle != null) return _playCircle!!
        _playCircle = materialIcon(name = "Filled.PlayCircle") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(9.5f, 16.5f)
                verticalLineToRelative(-9.0f)
                lineToRelative(7.0f, 4.5f)
                lineTo(9.5f, 16.5f)
                close()
            }
        }
        return _playCircle!!
    }

private var _playCircle: ImageVector? = null

val Icons.Filled.Print: ImageVector
    get() {
        if (_print != null) return _print!!
        _print = materialIcon(name = "Filled.Print") {
            materialPath {
                moveTo(19.0f, 8.0f)
                lineTo(5.0f, 8.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-6.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(16.0f, 19.0f)
                lineTo(8.0f, 19.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(5.0f)
                close()
                moveTo(19.0f, 12.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(18.0f, 3.0f)
                lineTo(6.0f, 3.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(12.0f)
                lineTo(18.0f, 3.0f)
                close()
            }
        }
        return _print!!
    }

private var _print: ImageVector? = null

val Icons.Filled.RestoreFromTrash: ImageVector
    get() {
        if (_restoreFromTrash != null) return _restoreFromTrash!!
        _restoreFromTrash = materialIcon(name = "Filled.RestoreFromTrash") {
            materialPath {
                moveTo(19.0f, 4.0f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1.0f, -1.0f)
                horizontalLineToRelative(-5.0f)
                lineToRelative(-1.0f, 1.0f)
                lineTo(5.0f, 4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                close()
                moveTo(6.0f, 7.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(18.0f, 7.0f)
                lineTo(6.0f, 7.0f)
                close()
                moveTo(14.0f, 14.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                lineTo(8.0f, 14.0f)
                lineToRelative(4.0f, -4.0f)
                lineToRelative(4.0f, 4.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }
        return _restoreFromTrash!!
    }

private var _restoreFromTrash: ImageVector? = null

val Icons.Filled.Rotate90DegreesCw: ImageVector
    get() {
        if (_rotate90DegreesCw != null) return _rotate90DegreesCw!!
        _rotate90DegreesCw = materialIcon(name = "Filled.Rotate90DegreesCw") {
            materialPath {
                moveTo(4.64f, 19.37f)
                curveToRelative(3.03f, 3.03f, 7.67f, 3.44f, 11.15f, 1.25f)
                lineToRelative(-1.46f, -1.46f)
                curveToRelative(-2.66f, 1.43f, -6.04f, 1.03f, -8.28f, -1.21f)
                curveToRelative(-2.73f, -2.73f, -2.73f, -7.17f, 0.0f, -9.9f)
                curveTo(7.42f, 6.69f, 9.21f, 6.03f, 11.0f, 6.03f)
                verticalLineTo(9.0f)
                lineToRelative(4.0f, -4.0f)
                lineToRelative(-4.0f, -4.0f)
                verticalLineToRelative(3.01f)
                curveToRelative(-2.3f, 0.0f, -4.61f, 0.87f, -6.36f, 2.63f)
                curveTo(1.12f, 10.15f, 1.12f, 15.85f, 4.64f, 19.37f)
                close()
                moveTo(11.0f, 13.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(6.0f, -6.0f)
                lineToRelative(-6.0f, -6.0f)
                lineTo(11.0f, 13.0f)
                close()
            }
        }
        return _rotate90DegreesCw!!
    }

private var _rotate90DegreesCw: ImageVector? = null

val Icons.Filled.Schedule: ImageVector
    get() {
        if (_schedule != null) return _schedule!!
        _schedule = materialIcon(name = "Filled.Schedule") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
            materialPath {
                moveTo(12.5f, 7.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
        return _schedule!!
    }

private var _schedule: ImageVector? = null
