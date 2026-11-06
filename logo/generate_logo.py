"""Generate raw logo candidates through a local Stable Diffusion WebUI API.

This is the design-time script that produced logo/raw_v*.png.
It is kept for provenance — you do NOT need it to build or run Kite.

Requires a running AUTOMATIC1111-style API. Override the endpoint with KITE_SD_API.
"""
import json
import os
import time
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

BASE = os.environ.get("KITE_SD_API", "http://127.0.0.1:8190")
OUT = str(Path(__file__).resolve().parent)
os.makedirs(OUT, exist_ok=True)

POSITIVE = (
    "Minimal premium app icon, flat vector logo, centered on a pure black background. "
    "An original futuristic autonomous car front emblem: a low, wide, symmetric wedge silhouette, "
    "very thin brushed-chrome outline, one continuous glowing red horizontal scanner slit, "
    "two narrow cool-white headlight slits, subtle dark red glow behind the scanner. "
    "Clean negative space, sharp geometric edges, high contrast, automotive luxury badge, "
    "obsidian black #080A0E, scanner red #FF3B30, chrome #C6CEDA. "
    "No perspective, no wheels, no grille mesh, no license plate, no text, no letters, no words, "
    "no watermark, no border, no frame, no 3D render, no photo, no reflections, no clutter."
)
NEGATIVE = (
    "text, letters, words, typography, watermark, signature, logo brand, KITT, Pontiac, Trans Am, "
    "license plate, grille mesh, wheels, windshield wipers, background scenery, road, garage, "
    "photorealistic, 3d render, plastic, glossy toy, chrome ball, lens flare, bokeh, blur, "
    "asymmetric, crooked, low contrast, muddy, noisy, clutter, multiple cars, face, robot face, "
    "border, frame, white background, grey background, gradient background"
)

VARIANTS = [
    ("v1_flat_wedge", POSITIVE),
    ("v2_badge", POSITIVE.replace(
        "flat vector logo", "luxury enamel badge logo"
    ).replace(
        "very thin brushed-chrome outline", "thin polished chrome bevel"
    )),
    ("v3_negative_space", POSITIVE.replace(
        "An original futuristic autonomous car front emblem", 
        "An original negative-space car front emblem formed by two angular chrome strokes"
    )),
    ("v4_monogram", (
        "Minimal premium app icon on pure black. An original geometric monogram 'K' built from "
        "two low wide car-hood strokes and one red horizontal scanner slit crossing the center, "
        "thin chrome edges, subtle red glow, sharp flat vector, symmetrical, no text, no letters, "
        "no watermark, no border, no 3D, no photo, no car brand."
    )),
]


def workflow(prompt, seed):
    return {
        "1": {"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": "Qwen_Image-Q4_K_M.gguf"}},
        "2": {"class_type": "CLIPLoader", "inputs": {
            "clip_name": "qwen_2.5_vl_7b_fp8_scaled.safetensors", "type": "qwen_image", "device": "default"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "qwen_image_vae.safetensors"}},
        "4": {"class_type": "ModelSamplingAuraFlow", "inputs": {"model": ["1", 0], "shift": 3.1}},
        "5": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["2", 0], "text": prompt}},
        "6": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["2", 0], "text": NEGATIVE}},
        "7": {"class_type": "EmptySD3LatentImage", "inputs": {"width": 1024, "height": 1024, "batch_size": 1}},
        "8": {"class_type": "KSampler", "inputs": {
            "model": ["4", 0], "seed": seed, "steps": 20, "cfg": 2.5,
            "sampler_name": "euler", "scheduler": "simple",
            "positive": ["5", 0], "negative": ["6", 0], "latent_image": ["7", 0], "denoise": 1.0}},
        "9": {"class_type": "VAEDecode", "inputs": {"samples": ["8", 0], "vae": ["3", 0]}},
        "10": {"class_type": "SaveImage", "inputs": {"images": ["9", 0], "filename_prefix": "kite_logo/{}".format(seed)}},
    }


def post(path, obj):
    data = json.dumps(obj).encode()
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=30) as r:
        return json.loads(r.read().decode())


for index, (name, prompt) in enumerate(VARIANTS):
    seed = 2026110500 + index * 37
    payload = {"prompt": workflow(prompt, seed), "client_id": str(uuid.uuid4())}
    try:
        result = post("/prompt", payload)
        pid = result["prompt_id"]
        print("submitted", name, pid, flush=True)
        deadline = time.time() + 420
        images = []
        while time.time() < deadline:
            time.sleep(3)
            hist = get("/history/" + pid)
            if pid in hist:
                outputs = hist[pid].get("outputs", {})
                images = outputs.get("10", {}).get("images", [])
                break
        if not images:
            print("no image", name, flush=True)
            continue
        info = images[0]
        query = urllib.parse.urlencode({
            "filename": info["filename"],
            "subfolder": info.get("subfolder", ""),
            "type": info.get("type", "output"),
        })
        data = urllib.request.urlopen(BASE + "/view?" + query, timeout=60).read()
        path = os.path.join(OUT, "raw_%s.png" % name)
        open(path, "wb").write(data)
        print("saved", path, len(data), flush=True)
    except Exception as exc:
        print("FAILED", name, repr(exc), flush=True)
