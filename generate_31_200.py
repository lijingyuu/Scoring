import asyncio
import os
import edge_tts

# 1. 定义 31 到 200 的数字映射
AUDIO_MAP = {str(i): str(i) for i in range(31, 201)}

# 2. 保持与之前完全一致的音色
VOICE = "zh-CN-YunxiNeural"

# 输出目录
OUTPUT_DIR = "./src/static/audio"


async def generate_single_audio(filename, text, sem):
    async with sem:
        output_path = os.path.join(OUTPUT_DIR, f"{filename}.mp3")

        # 检查是否已存在，避免重复请求
        if os.path.exists(output_path):
            print(f"已存在，跳过: {filename}.mp3")
            return

        try:
            communicate = edge_tts.Communicate(text, VOICE)
            await communicate.save(output_path)
            print(f"已生成: {filename}.mp3 (读作: {text})")
        except Exception as e:
            print(f"生成失败 {filename}.mp3: {e}")


async def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    # 并发限制为 5，兼顾速度与稳定性
    sem = asyncio.Semaphore(5)

    print("开始补齐 31 到 200 的数字音频...")
    tasks = [
        generate_single_audio(fname, text, sem)
        for fname, text in AUDIO_MAP.items()
    ]
    await asyncio.gather(*tasks)
    print(
        f"\n全部音频补齐完毕！文件保存在: {os.path.abspath(OUTPUT_DIR)}"
    )


if __name__ == "__main__":
    asyncio.run(main())