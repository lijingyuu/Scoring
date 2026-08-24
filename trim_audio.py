import os
import subprocess
from pathlib import Path

# 1. 路径定义
PROJECT_ROOT = Path(__file__).resolve().parent
INPUT_DIR = PROJECT_ROOT / "src" / "static" / "audio"
OUTPUT_DIR = PROJECT_ROOT / "src" / "static" / "audio_trimmed"

# 确保输出目录存在
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# 2. 静音裁剪滤镜参数（不包含任何加速，纯裁剪）
# start_silence=0.03: 开头保留 30ms 缓冲，防止“4、7、10”起音被吞
# stop_silence=0.04: 结尾保留 40ms 缓冲，防止尾音截断
SILENCE_FILTER = (
    "silenceremove="
    "start_periods=1:start_duration=0.02:start_threshold=-45dB:start_silence=0.03:"
    "stop_periods=1:stop_duration=0.02:stop_threshold=-45dB:stop_silence=0.04"
)

def should_process(file_name: str) -> bool:
    """过滤目标文件：只处理 bi.mp3 和 0~200 的数字音频"""
    stem = file_name.replace(".mp3", "")
    if stem == "bi":
        return True
    if stem.isdigit() and 0 <= int(stem) <= 200:
        return True
    return False

def run_trim():
    if not INPUT_DIR.exists():
        print(f"错误: 找不到输入目录 {INPUT_DIR}")
        return

    all_files = [f for f in INPUT_DIR.glob("*.mp3") if should_process(f.name)]
    total = len(all_files)
    print(f"共扫描到 {total} 个待裁剪文件，开始处理...\n")

    success_count = 0
    for idx, src_path in enumerate(all_files, start=1):
        dst_path = OUTPUT_DIR / src_path.name
        cmd = [
            "ffmpeg", "-y",
            "-i", str(src_path),
            "-af", SILENCE_FILTER,
            "-acodec", "libmp3lame",
            "-q:a", "2",
            str(dst_path)
        ]

        result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode == 0:
            success_count += 1
            print(f"[{idx}/{total}] 已处理: {src_path.name}")
        else:
            print(f"[{idx}/{total}] 处理失败: {src_path.name}")

    print(f"\n全部完成！成功处理 {success_count}/{total} 个文件。")
    print(f"裁剪后文件保存在: {OUTPUT_DIR}")

if __name__ == "__main__":
    run_trim()