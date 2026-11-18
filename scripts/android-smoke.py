"""Synthetic-only UI smoke. No Google account, cloud call or release operation."""
import subprocess, time, pathlib, xml.etree.ElementTree as ET, re
out=pathlib.Path('evidence');out.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args])
def dump():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
def node(label):
    root=dump()
    return next((n for n in root.iter('node') if n.get('text','').casefold()==label.casefold()),None)
def tap(label):
    for _ in range(12):
        n=node(label)
        if n is not None and n.get('enabled')=='true':
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            if y2>y1 and x2>x1:
                adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
        if n is None:
            adb('shell','input','swipe','500','1500','500','700','300')
        time.sleep(.4)
    screenshot('failure-'+re.sub('[^a-z]+','-',label.lower()))
    raise AssertionError('Missing enabled control: '+label)
def screenshot(name):
    out.joinpath(name+'.png').write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    out.joinpath(name+'.xml').write_bytes(adb('shell','cat','/sdcard/window.xml'))
def assert_text(fragment):
    for _ in range(15):
        texts='\n'.join(n.get('text','') for n in dump().iter('node'))
        if fragment in texts:return
        time.sleep(.3)
    raise AssertionError('Text not visible: '+fragment)
adb('install','-r','android/app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','start','-W','-n','com.vanipy.tampung/.MainActivity')
assert_text('tampung');screenshot('01-initial')
tap('Generate dummy notes');tap('Create local test snapshot')
tap('Clear local dummy notes');tap('Clear dummy notes')
tap('Restore local test snapshot');tap('Restore dummy notes')
# Scroll to status, inspect restore result and retained dummy fields.
adb('shell','input','swipe','500','1700','500','300','600')
time.sleep(.5)
adb('shell','input','swipe','500','1700','500','300','600')
assert_text('LOCAL simulation restored. No Google request.')
assert_text('Synthetic note: green box');assert_text('Uji cadangan. Dummy data only.')
screenshot('02-restored-local')
# Return to the cloud controls and verify no official account dialog was entered.
adb('shell','input','swipe','500','400','500','1450','600')
time.sleep(.4)
root=dump();cloud=[n for n in root.iter('node') if n.get('text') in ['Back up dummy notes to Drive','Choose a Drive snapshot']]
if len(cloud)!=2:
    adb('shell','input','swipe','500','400','500','1450','600');root=dump();cloud=[n for n in root.iter('node') if n.get('text') in ['Back up dummy notes to Drive','Choose a Drive snapshot']]
assert len(cloud)==2, 'Cloud controls not found'
assert all(n.get('enabled')=='false' for n in cloud),'Unconfigured cloud control enabled'
screenshot('03-disabled-google')
out.joinpath('result.txt').write_text('PASS: Android local dummy generate, snapshot, clear, restore and exact fields. Google controls disabled. No account or cloud test.\n')
print(out.joinpath('result.txt').read_text())
