"""Renders the Kempny Kino "K" mark (gradient stem + two arms) as transparent appIcon.png.

Geometry and colours follow the supplied artwork (400 x 410 reference box). Rendered at 2x
and downsampled for smooth edges. Pure PIL, no numpy required.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else "appIcon.png"
SIZE = 1254
SS = 2  # supersampling
REF_W, REF_H = 400, 410
SCALE = SIZE * SS / 430  # leaves a small margin around the 400 x 410 box
OFF_X = (SIZE * SS - REF_W * SCALE) / 2
OFF_Y = (SIZE * SS - REF_H * SCALE) / 2


def P(x, y):
	return (OFF_X + x * SCALE, OFF_Y + y * SCALE)


def hex_rgb(value):
	value = value.lstrip("#")
	return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def color_at(stops, t):
	t = max(0.0, min(1.0, t))
	for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
		if t <= t1:
			f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
			return tuple(round(a + (b - a) * f) for a, b in zip(c0, c1))
	return stops[-1][1]


def gradient(size, start, end, stops, step=4):
	"""Linear gradient image of [size] running from [start] to [end] (pixel coordinates).

	Computed at 1/[step] resolution and upscaled — gradients are smooth, this keeps memory low.
	"""
	full = size
	size = (size[0] // step, size[1] // step)
	start = (start[0] / step, start[1] / step)
	end = (end[0] / step, end[1] / step)
	stops = [(t, hex_rgb(c)) for t, c in stops]
	dx, dy = end[0] - start[0], end[1] - start[1]
	length = math.hypot(dx, dy)
	angle = math.degrees(math.atan2(dy, dx))

	# Horizontal ramp, big enough to cover the canvas after rotation.
	span = int(math.hypot(*size)) * 2 + 4
	ramp = Image.new("RGB", (span, 1))
	px = ramp.load()
	for x in range(span):
		s = x - span / 2  # distance from the gradient midpoint along the axis
		px[x, 0] = color_at(stops, (s + length / 2) / length)
	ramp = ramp.resize((span, span), Image.NEAREST)
	ramp = ramp.rotate(-angle, resample=Image.BICUBIC)

	mid = ((start[0] + end[0]) / 2, (start[1] + end[1]) / 2)
	left = int(span / 2 - mid[0])
	top = int(span / 2 - mid[1])
	return ramp.crop((left, top, left + size[0], top + size[1])).resize(full, Image.BICUBIC)


def capsule_mask(size, a, b, width):
	mask = Image.new("L", size, 0)
	d = ImageDraw.Draw(mask)
	d.line([a, b], fill=255, width=int(width))
	r = width / 2
	for (x, y) in (a, b):
		d.ellipse([x - r, y - r, x + r, y + r], fill=255)
	return mask


def main():
	size = (SIZE * SS, SIZE * SS)
	canvas = Image.new("RGBA", size, (0, 0, 0, 0))
	arm = 92 * SCALE

	# Upper arm: purple shadow at the junction, warm peach/apricot towards the end.
	a, b = P(150, 214), P(318, 94)
	g = gradient(size, a, b, [(0, "#A07ED9"), (0.3, "#D6A39C"), (0.7, "#EDB884"), (1, "#F4C57C")])
	canvas.paste(g, (0, 0), capsule_mask(size, a, b, arm))

	# Lower arm: violet, overlapping the upper arm at the junction.
	a, b = P(150, 236), P(316, 318)
	g = gradient(size, a, b, [(0, "#7A60D6"), (0.5, "#9172E4"), (1, "#A07BEE")])
	canvas.paste(g, (0, 0), capsule_mask(size, a, b, arm))

	# Stem on top: yellow at the top through peach and pink to violet.
	stem_l, stem_r = P(70, 25), P(156, 385)
	mask = Image.new("L", size, 0)
	ImageDraw.Draw(mask).rounded_rectangle(
		[stem_l[0], stem_l[1], stem_r[0], stem_r[1]],
		radius=(stem_r[0] - stem_l[0]) / 2,
		fill=255,
	)
	g = gradient(size, (stem_l[0], stem_l[1]), (stem_l[0], stem_r[1]),
		[(0, "#F8D872"), (0.35, "#EFBC82"), (0.7, "#D79AB0"), (1, "#B07BE0")])
	canvas.paste(g, (0, 0), mask)

	canvas = canvas.resize((SIZE, SIZE), Image.LANCZOS)
	canvas.save(OUT, "PNG", optimize=True)
	print("wrote", os.path.abspath(OUT), canvas.size)


if __name__ == "__main__":
	main()



