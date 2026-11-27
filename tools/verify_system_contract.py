"""Offline checks for notification collection, heads-up routing and freeform APIs.

The folder must contain SystemUI.apk, framework.jar and vivo-framework.jar.
Does not execute vendor code or claim runtime compatibility.
"""
import argparse
from pathlib import Path
import re
import zipfile
from dex_contract import definitions, field_definitions, read_dex

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('folder',type=Path)
parser.add_argument('--profile',choices=['os5','os6','os7'],required=True)
args=parser.parse_args()
count=0

def load(name):
    definitions.clear();field_definitions.clear()
    with zipfile.ZipFile(args.folder/name) as archive:
        for n in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex',n):read_dex(archive.read(n))

def method(owner,name,params,result=None):
    global count
    flags,actual=definitions[owner][name,params]
    assert flags&1,f'Not public: {owner}.{name}'
    assert result is None or actual==result,f'Wrong return type: {owner}.{name}: {actual}'
    count+=1
    return actual

def field(owner,name,expected=None):
    global count
    flags,actual=field_definitions[owner][name]
    assert flags&1,f'Not public: {owner}.{name}'
    assert expected is None or actual==expected,f'Wrong field type: {owner}.{name}: {actual}'
    count+=1

load('SystemUI.apk')
dependency='Lcom/android/systemui/Dependency;'
if args.profile=='os5':
    method(dependency,'get','Ljava/lang/Class;','Ljava/lang/Object;')
else:
    field(dependency,'sDependency')
    method(dependency,'getDependencyInner','Ljava/lang/Object;','Ljava/lang/Object;')
field('Lcom/vivo/systemui/plugin/VivoStatusBarIsLandInjector;','mIStatusBarIsland','Lcom/android/systemui/plugins/vivo/IStatusBarIsland;')
listener='Lcom/android/systemui/statusbar/NotificationListener;'
for callback in ['onNotificationPosted','onNotificationRemoved']:
    matches=[(name,p) for name,p in definitions[listener] if name==callback and p.startswith('Landroid/service/notification/StatusBarNotification;')]
    assert matches,f'Missing callback: {callback}'
    for name,p in matches:method(listener,name,p,'V')
entry='Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;'
if args.profile=='os5':method(entry,'getSbn','','Landroid/service/notification/StatusBarNotification;')
else:field(entry,'mSbn','Landroid/service/notification/StatusBarNotification;')
legacy='Lcom/android/systemui/statusbar/notification/interruption/NotificationInterruptStateProviderImpl;'
modern='Lcom/android/systemui/statusbar/notification/interruption/VisualInterruptionDecisionProviderImpl;'
if args.profile in ['os5','os6']:
    method(legacy,'checkHeadsUp',entry+'Z','Z')
    if args.profile=='os5':
        decision=method(modern,'makeAndLogHeadsUpDecision',entry)
        method(modern,'makeUnloggedHeadsUpDecision',entry,decision)
        method(modern[:-1]+'$DecisionImpl;','<init>','ZLjava/lang/String;','V')
        method(decision,'getShouldInterrupt','','Z')
else:
    assert legacy not in definitions,'Review runtime selection: legacy provider unexpectedly exists'
    decision=method(modern,'makeAndLogHeadsUpDecision',entry)
    method(modern,'makeUnloggedHeadsUpDecision',entry,decision)
    method(decision,'<init>','ZLjava/lang/String;','V')
    field(decision,'isValid','Z')

load('vivo-framework.jar')
manager='Lvivo/app/vivofreeform/VivoFreeformManager;'
method(manager,'getInstance','',manager)
method(manager,'isVivoFreeFormSupportsApp','Ljava/lang/String;','Z')

load('framework.jar')
if args.profile!='os5':method('Landroid/view/IWindowManager;','getFreeformBoundsForNotificationPull','ZI','Landroid/graphics/Rect;')
else:print('INFO: OS5 has no freeform-bounds query; existing CardMotion geometric fallback is used.')
method('Landroid/app/PendingIntent;','getIntent','','Landroid/content/Intent;')
print(f'PASS: {count} public SystemUI/framework method and field contracts for {args.profile}.')

# 0.1.24 resolves sender images in SystemUI and transfers bounded pixels to the
# island process. Check the actual ROM framework, not only the compile SDK stubs.
avatar_start=count
for owner,name,params,result in [
    ('Landroid/app/Notification;','getLargeIcon','','Landroid/graphics/drawable/Icon;'),
    ('Landroid/app/Person;','getIcon','','Landroid/graphics/drawable/Icon;'),
    ('Landroid/app/Person;','getName','','Ljava/lang/CharSequence;'),
    ('Landroid/graphics/drawable/Icon;','getType','','I'),
    ('Landroid/graphics/drawable/Icon;','loadDrawable','Landroid/content/Context;','Landroid/graphics/drawable/Drawable;'),
    ('Landroid/graphics/drawable/Icon;','createWithBitmap','Landroid/graphics/Bitmap;','Landroid/graphics/drawable/Icon;'),
    ('Landroid/os/Bundle;','getParcelable','Ljava/lang/String;Ljava/lang/Class;','Ljava/lang/Object;'),
    ('Landroid/os/Bundle;','getParcelableArrayList','Ljava/lang/String;Ljava/lang/Class;','Ljava/util/ArrayList;'),
    ('Landroid/os/Bundle;','getParcelableArray','Ljava/lang/String;','[Landroid/os/Parcelable;'),
    ('Landroid/content/Context;','createPackageContext','Ljava/lang/String;I','Landroid/content/Context;'),
]:
    method(owner,name,params,result)
field('Landroid/app/Notification;','extras','Landroid/os/Bundle;')
for name in ['EXTRA_MESSAGES','EXTRA_PEOPLE_LIST','EXTRA_LARGE_ICON','EXTRA_TITLE']:
    field('Landroid/app/Notification;',name,'Ljava/lang/String;')
print(f'PASS: {count-avatar_start} sender-avatar framework contracts for {args.profile}.')
