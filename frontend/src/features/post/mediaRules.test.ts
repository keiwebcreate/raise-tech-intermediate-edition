import { describe, expect, it } from 'vitest'
import {
  checkImageCount,
  checkImageFile,
  checkVideoDuration,
  checkVideoFile,
  gridAreas,
  IMAGE_MAX_BYTES,
  MEDIA_MESSAGES,
  VIDEO_MAX_BYTES,
} from './mediaRules.ts'

describe('checkImageFile', () => {
  it('JPEG・PNG・WebP・GIF の5MBまでは通す', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp', 'image/gif']) {
      expect(checkImageFile({ type, size: IMAGE_MAX_BYTES })).toBeNull()
    }
  })

  it('ほかの種類・5MBを超えるものは断る', () => {
    expect(checkImageFile({ type: 'image/bmp', size: 10 })).toBe(MEDIA_MESSAGES.imageType)
    expect(checkImageFile({ type: 'image/heic', size: 10 })).toBe(MEDIA_MESSAGES.imageType)
    expect(checkImageFile({ type: 'image/png', size: IMAGE_MAX_BYTES + 1 })).toBe(MEDIA_MESSAGES.imageSize)
  })
})

describe('checkVideoFile', () => {
  it('MP4 の100MBまでは通し、MOV や100MB超は断る', () => {
    expect(checkVideoFile({ type: 'video/mp4', size: VIDEO_MAX_BYTES })).toBeNull()
    expect(checkVideoFile({ type: 'video/quicktime', size: 10 })).toBe(MEDIA_MESSAGES.videoType)
    expect(checkVideoFile({ type: 'video/mp4', size: VIDEO_MAX_BYTES + 1 })).toBe(MEDIA_MESSAGES.videoLimit)
  })
})

describe('checkVideoDuration', () => {
  it('140秒までは通し、140秒より長いものは断る（サーバーと同じ数え方）', () => {
    expect(checkVideoDuration(140)).toBeNull()
    expect(checkVideoDuration(140.01)).toBe(MEDIA_MESSAGES.videoLimit)
  })

  it('長さが読めないものは動画として扱えない', () => {
    expect(checkVideoDuration(Number.NaN)).toBe(MEDIA_MESSAGES.videoType)
    expect(checkVideoDuration(Number.POSITIVE_INFINITY)).toBe(MEDIA_MESSAGES.videoType)
  })
})

describe('checkImageCount', () => {
  it('合わせて4枚までは足せる', () => {
    expect(checkImageCount(0, 4)).toBeNull()
    expect(checkImageCount(3, 1)).toBeNull()
    expect(checkImageCount(3, 2)).toBe(MEDIA_MESSAGES.imageMax)
  })
})

describe('gridAreas', () => {
  it('3枚は左に1枚大きく、右に2枚', () => {
    expect(gridAreas(1)).toBe('"a"')
    expect(gridAreas(2)).toBe('"a b"')
    expect(gridAreas(3)).toBe('"a b" "a c"')
    expect(gridAreas(4)).toBe('"a b" "c d"')
  })
})
