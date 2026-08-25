# R8 rules for the release build.
#
# Deliberately short. Room, Compose, kotlinx.serialization and
# play-services-location all ship consumer rules inside their own
# artifacts, so what is left here is what only this app knows: which of
# its names are written down somewhere and read back by string.

# ---------------------------------------------------------------------
# Enum constant names are persisted data.
#
# Settings store DistanceProviderType, DistanceUnit, MeasurementSystem
# and WatchAccuracy in DataStore by .name and read them back with
# valueOf(); RideRecord.distanceProvider goes through the same round trip
# into a database column (Converters); and every @Serializable enum is
# encoded into exported JSON by constant name.
#
# So renaming a constant is not an obfuscation, it is a data format
# change - and the failure it causes is the worst kind: an existing
# install throwing IllegalArgumentException while reading its settings,
# at launch, before anything is on screen. The whole model package is
# kept rather than the four enums that need it today, because the next
# enum added there will need it too and nobody will remember this file.
-keepclassmembers enum org.fossridemeter.app.model.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------
# Crash reports from a shrunk build are unreadable without these. They
# cost a little size and no behaviour; the mapping file that turns them
# back into source lines is written to
# app/build/outputs/mapping/release/mapping.txt and must be kept for any
# build handed to anyone.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
