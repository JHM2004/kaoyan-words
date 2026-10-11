import random
import re

# 读取 words.md
with open('words.md', 'r', encoding='utf-8') as f:
    lines = f.readlines()

words = []
for line in lines:
    line = line.strip()
    if not line or line.startswith('###'):
        continue
    # 按第一个空白分割：单词 + 释义
    parts = re.split(r'\s+', line, maxsplit=1)
    if len(parts) == 2:
        word, meaning = parts
        words.append((word, meaning))

# 所有释义列表
all_meanings = [m for _, m in words]

random.seed(2024)  # 固定随机种子，结果可复现
output_lines = []
used_as_distractor = set()  # 记录已经当过干扰项的释义，尽量不重复

for word, correct in words:
    # 候选干扰项：其他单词的释义，且不等于正确释义
    candidates = [m for m in all_meanings if m != correct]
    # 优先选还没当过干扰项的
    unused = [m for m in candidates if m not in used_as_distractor]
    if len(unused) >= 3:
        distractors = random.sample(unused, 3)
    else:
        # 不够就从所有候选中补，但保证同一题内不重复
        distractors = unused[:]
        remaining = [m for m in candidates if m not in distractors]
        random.shuffle(remaining)
        for m in remaining:
            if len(distractors) >= 3:
                break
            if m not in distractors:
                distractors.append(m)
    # 最终确保三个干扰项互不相同
    distractors = list(dict.fromkeys(distractors))
    while len(distractors) < 3:
        extra = random.choice(candidates)
        if extra not in distractors:
            distractors.append(extra)
    # 记录已用
    for d in distractors:
        used_as_distractor.add(d)
    # 输出：A 正确，B/C/D 干扰
    output_lines.append(f"{word} A. {correct} B. {distractors[0]} C. {distractors[1]} D. {distractors[2]}")

with open('words_quiz.md', 'w', encoding='utf-8') as f:
    f.write('\n'.join(output_lines))

print(f"生成完成，共 {len(output_lines)} 题，保存到 words_quiz.md")