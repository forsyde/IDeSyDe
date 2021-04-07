# -*- mode: python ; coding: utf-8 -*-
import sys
sys.setrecursionlimit(2000)

block_cipher = None


a = Analysis(['idesyde/cli.py'],
             pathex=['/home/rjordao/ForSyDe/tools/IDeSyDe/idesyde'],
             binaries=[],
             datas=[('idesyde/minizinc/*.mzn', '.')],
             hiddenimports=[],
             hookspath=[],
             runtime_hooks=[],
             excludes=[],
             win_no_prefer_redirects=False,
             win_private_assemblies=False,
             cipher=block_cipher,
             noarchive=False)
pyz = PYZ(a.pure, a.zipped_data,
             cipher=block_cipher)
exe = EXE(pyz,
          a.scripts,
          [],
          exclude_binaries=False,
          name='idesyde',
          debug=False,
          bootloader_ignore_signals=False,
          strip=False,
          upx=True,
          console=True )
coll = COLLECT(exe,
               a.binaries,
               a.zipfiles,
               a.datas,
               strip=False,
               upx=True,
               upx_exclude=[],
               name='idesyde')
