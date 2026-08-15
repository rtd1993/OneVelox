using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

internal static class Program
{
    private static int Main(string[] args)
    {
        var dir = args.Length > 0 ? args[0] : @"C:\Users\rtd19\Desktop\OneVelox app\app\src\main\res\drawable-nodpi";
        var files = Directory.GetFiles(dir, "ic_vehicle_*.png");
        Console.WriteLine("Processing {0} files in {1}", files.Length, dir);
        foreach (var file in files)
        {
            var name = Path.GetFileName(file);
            var whiteBody = name.IndexOf("_bianco", StringComparison.OrdinalIgnoreCase) >= 0;
            using (var src = new Bitmap(file))
            using (var argb = src.Clone(new Rectangle(0, 0, src.Width, src.Height), PixelFormat.Format32bppArgb))
            {
                KnockOutBackground(argb, whiteBody);
                using (var resized = Resize(argb, 512, 512))
                {
                    var tmp = file + ".tmp.png";
                    resized.Save(tmp, ImageFormat.Png);
                }
            }
            File.Delete(file);
            File.Move(file + ".tmp.png", file);
            Console.WriteLine("  {0}", name);
        }
        return 0;
    }

    private static void KnockOutBackground(Bitmap bmp, bool whiteBody)
    {
        var rect = new Rectangle(0, 0, bmp.Width, bmp.Height);
        var data = bmp.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        try
        {
            int w = data.Width;
            int h = data.Height;
            int stride = data.Stride;
            var bytes = new byte[stride * h];
            Marshal.Copy(data.Scan0, bytes, 0, bytes.Length);

            int minChannel = whiteBody ? 239 : 236;
            int maxSpread = whiteBody ? 14 : 18;

            var visited = new bool[w * h];
            var q = new Queue<int>(w * 4);

            Action<int, int> enqueueIfBg = (x, y) =>
            {
                if (x < 0 || y < 0 || x >= w || y >= h) return;
                int idx = y * w + x;
                if (visited[idx]) return;
                int o = y * stride + x * 4;
                byte b = bytes[o], g = bytes[o + 1], r = bytes[o + 2];
                int mn = Math.Min(r, Math.Min(g, b));
                int mx = Math.Max(r, Math.Max(g, b));
                if (mn < minChannel || (mx - mn) > maxSpread) return;
                visited[idx] = true;
                bytes[o + 3] = 0;
                q.Enqueue(idx);
            };

            for (int x = 0; x < w; x++)
            {
                enqueueIfBg(x, 0);
                enqueueIfBg(x, h - 1);
            }
            for (int y = 0; y < h; y++)
            {
                enqueueIfBg(0, y);
                enqueueIfBg(w - 1, y);
            }

            while (q.Count > 0)
            {
                int idx = q.Dequeue();
                int x = idx % w;
                int y = idx / w;
                enqueueIfBg(x + 1, y);
                enqueueIfBg(x - 1, y);
                enqueueIfBg(x, y + 1);
                enqueueIfBg(x, y - 1);
            }

            // Soften leftover halo: fade near-white pixels that touch transparency.
            for (int y = 1; y < h - 1; y++)
            {
                for (int x = 1; x < w - 1; x++)
                {
                    int o = y * stride + x * 4;
                    if (bytes[o + 3] == 0) continue;
                    byte b = bytes[o], g = bytes[o + 1], r = bytes[o + 2];
                    int mn = Math.Min(r, Math.Min(g, b));
                    int mx = Math.Max(r, Math.Max(g, b));
                    if (mn < 232 || (mx - mn) > 14) continue;
                    bool nearClear = false;
                    for (int dy = -1; dy <= 1 && !nearClear; dy++)
                    {
                        for (int dx = -1; dx <= 1; dx++)
                        {
                            int n = (y + dy) * stride + (x + dx) * 4;
                            if (bytes[n + 3] == 0) { nearClear = true; break; }
                        }
                    }
                    if (nearClear) bytes[o + 3] = (byte)Math.Max(0, mn - 220);
                }
            }

            Marshal.Copy(bytes, 0, data.Scan0, bytes.Length);
        }
        finally
        {
            bmp.UnlockBits(data);
        }
    }

    private static Bitmap Resize(Bitmap src, int width, int height)
    {
        var dest = new Bitmap(width, height, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(dest))
        {
            g.CompositingMode = CompositingMode.SourceCopy;
            g.CompositingQuality = CompositingQuality.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.SmoothingMode = SmoothingMode.HighQuality;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(src, new Rectangle(0, 0, width, height));
        }
        return dest;
    }
}
