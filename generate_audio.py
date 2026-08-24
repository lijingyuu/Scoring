import asyncio
import os
import edge_tts

# 1. 定义需要生成的音频列表 { 文件名 : 播报文字 }
AUDIO_MAP = {
    # 0 到 30 基础比分
    **{str(i): str(i) for i in range(31)},
    # 常用裁判术语
    "bi": "比",
    "ping": "平",
    "sheng": "胜",
    "point": "局点",
    "match_point": "场点",
    "change_side": "交换场地",
    "service_over": "换发球",
    "foul": "犯规",
    "cancel": "取消",
}

# 2. 选用音色：
# 'zh-CN-YunxiNeural' (云希：声音清晰、有力，非常适合体育裁判场景)
# 'zh-CN-XiaoxiaoNeural' (晓晓：清脆自然女声)
VOICE = "zh-CN-YunxiNeural"

# 输出目录（会自动创建）
OUTPUT_DIR = "./src/static/audio"


async def generate_single_audio(filename, text, sem):
    async with sem:
        output_path = os.path.join(OUTPUT_DIR, f"{filename}.mp3")
        # rate='+0%' 语速正常，pitch='+0Hz' 音调正常
        communicate = edge_tts.Communicate(text, VOICE)
        await communicate.save(output_path)
        print(f"已生成: {filename}.mp3 (内容: {text})")


async def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    # 限制并发数为 5，避免请求过快
    sem = asyncio.Semaphore(5)

    print("开始批量生成裁判音频...")
    tasks = [
        generate_single_audio(fname, text, sem)
        for fname, text in AUDIO_MAP.items()
    ]
    await asyncio.gather(*tasks)
    print(f"\n全部音频生成完毕！文件保存在: {os.path.abspath(OUTPUT_DIR)}")


if __name__ == "__main__":
    asyncio.run(main())