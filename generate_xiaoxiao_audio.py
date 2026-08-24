import asyncio
import os
import shutil
import subprocess
from pathlib import Path
import edge_tts

# ==================== 基础配置 ====================
VOICE = "zh-CN-XiaoxiaoNeural"  # 晓晓音色
PROJECT_ROOT = Path(__file__).resolve().parent

# 新输出目录（独立存放，不覆盖原有文件）
OUTPUT_DIR = PROJECT_ROOT / "src" / "static" / "audio_xiaoxiao"
TEMP_DIR = PROJECT_ROOT / "temp_tts_xiaoxiao_raw"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
TEMP_DIR.mkdir(parents=True, exist_ok=True)

# 专有名词对照表 (文件名: 文本)
SPECIAL_PHRASES = {
    "change_serve.mp3": "换发球",
    "game_point.mp3": "局点",
    "match_point.mp3": "场点",
}

# 改进后的安全双向裁剪滤镜：
# 1. 正向消除开头静音，保留 50ms 缓冲
# 2. 反转音频（areverse）消除结尾静音，保留 70ms 缓冲
# 3. 再次反转还原。此方案绝对不会误切词语中间的发音停顿！
SAFE_TRIM_FILTER = (
    "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-42dB:start_silence=0.05,"
    "areverse,"
    "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-42dB:start_silence=0.07,"
    "areverse"
)
# ==================================================


def num_to_chinese(n: int) -> str:
    """标准比分中文数字发音转换"""
    if n == 0:
        return "零"
    digits = ["", "一", "二", "三", "四", "五", "六", "七", "八", "九"]
    if 1 <= n <= 9:
        return digits[n]
    if 10 <= n <= 19:
        return "十" + digits[n % 10]
    if 20 <= n <= 99:
        return digits[n // 10] + "十" + digits[n % 10]
    if n == 100:
        return "一百"
    if 101 <= n <= 109:
        return "一百零" + digits[n % 10]
    if 110 <= n <= 119:
        return "一百一十" + digits[n % 10]
    if 120 <= n <= 199:
        return "一百" + digits[(n % 100) // 10] + "十" + digits[n % 10]
    if n == 200:
        return "二百"
    return str(n)


def trim_audio_safely(src_path: Path, dst_path: Path):
    """安全裁剪首尾静音"""
    cmd = [
        "ffmpeg", "-y",
        "-i", str(src_path),
        "-af", SAFE_TRIM_FILTER,
        "-acodec", "libmp3lame",
        "-q:a", "2",
        str(dst_path)
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


async def generate_single(text: str, filename: str, sem: asyncio.Semaphore):
    """并发生成单个音频"""
    async with sem:
        temp_file = TEMP_DIR / filename
        dst_file = OUTPUT_DIR / filename

        communicate = edge_tts.Communicate(text, VOICE)
        await communicate.save(str(temp_file))

        trim_audio_safely(temp_file, dst_file)


async def generate_natural_bi():
    """从上下文句子 '十二比八' 中精确提取半三声 '比'，保留充裕尾音缓冲"""
    context_text = "十二比八"
    temp_full = TEMP_DIR / "temp_context_bi.mp3"
    dst_file = OUTPUT_DIR / "bi.mp3"

    communicate = edge_tts.Communicate(context_text, VOICE)
    audio_bytes = bytearray()
    start_sec = None
    duration_sec = None

    async for chunk in communicate.stream():
        if chunk["type"] == "audio":
            audio_bytes.extend(chunk["data"])
        elif chunk["type"] == "WordBoundary":
            if "比" in chunk["text"]:
                start_sec = chunk["offset"] / 10_000_000
                duration_sec = chunk["duration"] / 10_000_000

    with open(temp_full, "wb") as f:
        f.write(audio_bytes)

    if start_sec is not None and duration_sec is not None:
        # 为“比”字前后各加 0.03s 的充裕安全余量，避免吞字
        adj_start = max(0, start_sec - 0.02)
        adj_duration = duration_sec + 0.06

        filter_complex = (
            f"atrim=start={adj_start:.3f}:duration={adj_duration:.3f},"
            f"asetpts=PTS-STARTPTS,"
            f"afade=t=in:st=0:d=0.015,"
            f"afade=t=out:st={adj_duration - 0.02:.3f}:d=0.02,"
            f"{SAFE_TRIM_FILTER}"
        )
        cmd = [
            "ffmpeg", "-y",
            "-i", str(temp_full),
            "-af", filter_complex,
            "-acodec", "libmp3lame",
            "-q:a", "2",
            str(dst_file)
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(" -> 【比】(半三声上下文版本) 提取完成")


async def main():
    print(f"=== 开始全量重新生成晓晓语音包 ===")
    print(f"目标目录: {OUTPUT_DIR}\n")
    
    sem = asyncio.Semaphore(5)
    tasks = []

    # 1. 0~200 数字
    print("1. 生成 0~200 数字音频...")
    for i in range(0, 201):
        text = num_to_chinese(i)
        filename = f"{i}.mp3"
        tasks.append(generate_single(text, filename, sem))

    # 2. 专有名词
    print("2. 生成 换发球 / 局点 / 场点 ...")
    for filename, text in SPECIAL_PHRASES.items():
        tasks.append(generate_single(text, filename, sem))

    await asyncio.gather(*tasks)
    print(" -> 数字与专有名词已全部生成完毕。")

    # 3. 提取半三声比字
    print("3. 提取半三声【比】字...")
    await generate_natural_bi()

    # 清理临时目录
    shutil.rmtree(TEMP_DIR, ignore_errors=True)
    print(f"\n 全部成功！音频已保存在新文件夹：\n{OUTPUT_DIR}")


if __name__ == "__main__":
    asyncio.run(main())