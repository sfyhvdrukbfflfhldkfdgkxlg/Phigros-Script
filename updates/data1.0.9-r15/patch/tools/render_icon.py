"""Render the original code-native launcher artwork at export and legacy sizes.

Requires Pillow only; Android uses the matching vector/adaptive XML source.
"""
from pathlib import Path
import argparse,math
from PIL import Image,ImageDraw

def icon(size):
    scale=4;side=size*scale;factor=side/108
    canvas=Image.new('RGBA',(side,side));draw=ImageDraw.Draw(canvas)
    draw.rounded_rectangle((0,0,side,side),radius=24*factor,fill='#0b111b')
    def curve(points):
        result=[]
        for i in range(65):
            t=i/64;u=1-t
            result.append(tuple(factor*(u*u*u*points[0][k]+3*u*u*t*points[1][k]+3*u*t*t*points[2][k]+t*t*t*points[3][k]) for k in range(2)))
        return result
    for x,sign in [(45,-1),(63,1)]:
        edge=54+27*sign;inner=54+21*sign
        points=curve([(x,31),(inner,34),(edge,42),(edge,54)])+curve([(edge,54),(edge,66),(inner,74),(x,77)])[1:]
        width=round(7*factor);draw.line(points,fill='#83e4ca',width=width,joint='curve')
        radius=width/2
        # Round joins are filled explicitly to avoid raster hairlines between
        # short Bezier polyline legs at large export sizes.
        for px,py in points:draw.ellipse((px-radius,py-radius,px+radius,py+radius),fill='#83e4ca')
    draw.rounded_rectangle((51*factor,20*factor,57*factor,78*factor),radius=3*factor,fill='#f0f5fc')
    draw.ellipse((50.5*factor,81.5*factor,57.5*factor,88.5*factor),fill='#ffb5b7')
    return canvas.resize((size,size),Image.Resampling.LANCZOS)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--export',type=Path);args=parser.parse_args()
    root=Path(__file__).resolve().parents[1];target=root/'icon/res/mipmap/ic_launcher.png';target.parent.mkdir(parents=True,exist_ok=True);icon(512).save(target,optimize=True)
    if args.export:args.export.parent.mkdir(parents=True,exist_ok=True);icon(1024).save(args.export,optimize=True)
    print('Launcher bitmap and vector source ready')
