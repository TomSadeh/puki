"""Builds libpuki_whisper.so (whisper.cpp + a JNI wrapper) for the app, into app/src/main/jniLibs.

    python native/whisper/build.py

Needs the Android NDK and CMake (Android Studio's SDK Manager: "NDK" and "CMake"). The built
libraries are committed, so building the app doesn't need this; run it when updating whisper.cpp.

Two arm64 builds: one using the i8mm instructions (much faster on recent phones, e.g. a Galaxy
S25) and a baseline one for older chips; the app picks at run time. Plus x86_64 for the emulator.
"""
import os
import shutil
import subprocess
import sys

WHISPER_REPO = "https://github.com/ggml-org/whisper.cpp"
WHISPER_COMMIT = "0c7be696371069e0baa2515ed7aa08d5a5473aa6"  # the build tested on a Galaxy S25

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(HERE, ".whisper.cpp")
SDK = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/AppData/Local/Android/Sdk" if os.name == "nt" else "~/Android/Sdk")

BUILDS = [  # (abi, library name, extra cmake args)
    ("arm64-v8a", "libpuki_whisper.so", ["-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16+i8mm"]),
    ("arm64-v8a", "libpuki_whisper_base.so", ["-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16"]),
    ("x86_64", "libpuki_whisper.so", []),
]


def run(cmd, cwd=None):
    print("  $", " ".join(cmd))
    subprocess.run(cmd, cwd=cwd, check=True)


def newest(path):
    dirs = sorted(os.listdir(path)) if os.path.isdir(path) else []
    if not dirs:
        sys.exit(f"nothing in {path}: install it with Android Studio's SDK Manager")
    return os.path.join(path, dirs[-1])


def main():
    ndk = newest(os.path.join(SDK, "ndk"))
    cmake_bin = os.path.join(newest(os.path.join(SDK, "cmake")), "bin")
    exe = ".exe" if os.name == "nt" else ""
    if not os.path.isdir(SRC):
        run(["git", "clone", "-q", "--depth", "1", WHISPER_REPO, SRC])
    run(["git", "-C", SRC, "fetch", "-q", "--depth", "1", "origin", WHISPER_COMMIT])
    run(["git", "-C", SRC, "checkout", "-q", WHISPER_COMMIT])
    for abi, name, extra in BUILDS:
        build = os.path.join(HERE, ".build", f"{abi}-{name}")
        run([os.path.join(cmake_bin, "cmake" + exe), "-S", HERE, "-B", build, "-G", "Ninja",
             f"-DCMAKE_MAKE_PROGRAM={os.path.join(cmake_bin, 'ninja' + exe)}",
             f"-DCMAKE_TOOLCHAIN_FILE={os.path.join(ndk, 'build', 'cmake', 'android.toolchain.cmake')}",
             f"-DANDROID_ABI={abi}", "-DANDROID_PLATFORM=android-31", "-DCMAKE_BUILD_TYPE=Release",
             f"-DWHISPER_DIR={SRC}", *extra])
        run([os.path.join(cmake_bin, "cmake" + exe), "--build", build, "--target", "puki_whisper", "-j", "8"])
        out_dir = os.path.join(ROOT, "app", "src", "main", "jniLibs", abi)
        os.makedirs(out_dir, exist_ok=True)
        shutil.copyfile(os.path.join(build, "libpuki_whisper.so"), os.path.join(out_dir, name))
        print(f"  -> {abi}/{name}  {os.path.getsize(os.path.join(out_dir, name)) // 1024} KB")


if __name__ == "__main__":
    main()
