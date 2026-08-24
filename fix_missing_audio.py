import asyncio
import os
import edge_tts

# 检查 0 到 200 以及裁判术语
AUDIO_MAP = {
    **{str(i): str(i) for i in range(201)},
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

VOICE = "zh-CN-YunxiNeural"
OUTPUT_DIR = "./src/static/audio"


async def fetch_with_retry(filename, text, max_retries=5):
    output_path = os.path.join(OUTPUT_DIR, f"{filename}.mp3")

    # 1. 校验文件是否存在且大小正常（> 1KB 视为有效文件）
    if os.path.exists(output_path) and os.path.getsize(output_path) > 1024:
        return True

    # 2. 失败重试逻辑
    for attempt in range(1, max_retries + 1):
        try:
            communicate = edge_tts.Communicate(text, VOICE)
            await communicate.save(output_path)

            # 再次确认生成的文件大小
            if (
                os.path.exists(output_path)
                and os.path.getsize(output_path) > 1024
            ):
                print(f"成功补齐: {filename}.mp3 (读作: {text})")
                return True
        except Exception as e:
            if attempt < max_retries:
                # 遇到限流时等待 1~2 秒再试
                await asyncio.sleep(1.2 * attempt)
            else:
                print(f"最终失败 {filename}.mp3: {e}")

    return False


async def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # 找出当前缺失或损坏的文件
    missing_items = []
    for fname, text in AUDIO_MAP.items():
        fpath = os.path.join(OUTPUT_DIR, f"{fname}.mp3")
        if not os.path.exists(fpath) or os.path.getsize(fpath) <= 1024:
            missing_items.append((fname, text))

    if not missing_items:
        print("所有 0~200 及术语音频均已完整存在，无需补齐！")
        return

    print(f"检测到共有 {len(missing_items)} 个音频缺失/损坏，开始补齐...\n")

    # 采用温和的单任务流 + 微延迟，避免被服务器限流
    success_count = 0
    for fname, text in missing_items:
        ok = await fetch_with_retry(fname, text)
        if ok:
            success_count += 1
        # 每次请求后稍微停顿 0.3 秒
        await asyncio.sleep(0.3)

    print(
        f"\n处理完成: 成功补齐 {success_count}/{len(missing_items)} 个文件。"
    )
    print(f"存放路径: {os.path.abspath(OUTPUT_DIR)}")


if __name__ == "__main__":
    asyncio.run(main())