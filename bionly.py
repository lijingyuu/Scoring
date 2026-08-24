import asyncio
import shutil
import subprocess
from pathlib import Path
import edge_tts

VOICE = "zh-CN-XiaoxiaoNeural"
PROJECT_ROOT = Path(__file__).resolve().parent

TEMP_FILE = PROJECT_ROOT / "temp_normal_bi.mp3"
DST_XIAOXIAO = PROJECT_ROOT / "src" / "static" / "audio_xiaoxiao" / "bi.mp3"
DST_AUDIO = PROJECT_ROOT / "src" / "static" / "audio" / "bi.mp3"

DST_XIAOXIAO.parent.mkdir(parents=True, exist_ok=True)
DST_AUDIO.parent.mkdir(parents=True, exist_ok=True)

# 仅做最基础的首尾静音消除，保留充足的 50ms 起止缓冲，保证发音原汁原味
SAFE_TRIM = (
    "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-42dB:start_silence=0.05,"
    "areverse,"
    "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-42dB:start_silence=0.07,"
    "areverse"
)


async def main():
    print("正在生成晓晓最纯正的标准单字【比】...")
    communicate = edge_tts.Communicate("比", VOICE)
    await communicate.save(str(TEMP_FILE))

    # 基础裁剪首尾多余空白
    cmd = [
        "ffmpeg", "-y",
        "-i", str(TEMP_FILE),
        "-af", SAFE_TRIM,
        "-acodec", "libmp3lame",
        "-q:a", "2",
        str(DST_XIAOXIAO)
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    # 同步覆盖到正式目录
    shutil.copyfile(DST_XIAOXIAO, DST_AUDIO)

    if TEMP_FILE.exists():
        TEMP_FILE.unlink()

    print(f" 生成完毕！已同步更新至：")
    print(f" - {DST_XIAOXIAO}")
    print(f" - {DST_AUDIO}")


if __name__ == "__main__":
    asyncio.run(main())