"""Check the native island DEX contract for a selected OriginOS adapter."""
import argparse
import zipfile
from dex_contract import definitions, field_definitions, read_dex

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', help='Path to your own OriginOS SystemUIPlugin.apk')
parser.add_argument('--profile', choices=['os5','os6','os7'], default='os6')
args = parser.parse_args()
with zipfile.ZipFile(args.apk) as archive:
    for name in archive.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            read_dex(archive.read(name))

prefix = 'Lcom/vivo/island/'
checked = 0
def require(owner, name, args='', public=True):
    global checked
    flags, result = definitions[prefix + owner + ';'][name, args]
    assert not public or flags & 1, f'{owner}.{name} is not public: {flags:#x}'
    checked += 1
    return flags

builder = 'data/AtomIslandsBean$Builder'
require(builder, '<init>')
for names, arg in [
    ('buildId buildNotificationId buildBusinessKey buildSourcePackage buildLandingInfoPkn', 'Ljava/lang/String;'),
    ('buildUserId buildAtomIslandsType buildPriority buildLeftTemplate buildRightTemplate buildIslandClick buildTemplateCard buildShowTime buildNewNode', 'I'),
    ('buildPermanent buildSupportKeyguardShowing buildSystemApp buildScrollRemove buildSupportNewNode buildForceShow', 'Z'),
    ('buildLeftContent buildRightContent', 'Ljava/lang/CharSequence;'),
    ('buildLeftIcon', 'Landroid/graphics/drawable/Icon;'),
    ('buildClickIslandResp', 'Landroid/app/PendingIntent;'),
    ('buildTimeout', 'J'),
    ('buildBusinessLoader', prefix+'data/AtomIslandLoader;'),
]:
    for name in names.split():
        if args.profile=='os5' and name in ['buildNotificationId','buildPermanent']:continue
        require(builder, name, arg)
require('data/AtomIslandsBean', 'getId')
require('data/AtomIslandsBean', 'forceShowIsland')
require('data/AtomIslandsDataController', 'handleAtomIslandReceiversData', 'Ljava/util/HashMap;')
require('controller/IslandWindowControllerImpl', 'applyVisible', prefix+'controller/IslandWindowState;'+('' if args.profile=='os5' else 'Ljava/lang/String;'), public=False)
for name in ['mPortrait','mForceIslandVisible']:
    flags, value = field_definitions[prefix+'controller/IslandWindowState;'][name]
    assert value=='Z' and flags & 8 and flags & 1, f'Unsupported landscape field {name}'
assert definitions[prefix+'data/AtomIslandsBean;']['forceShowIsland',''][1]=='Z'
assert definitions[prefix+'data/AtomIslandsDataController;']['handleAtomIslandReceiversData','Ljava/util/HashMap;'][1]=='V'
assert definitions[prefix+'controller/IslandWindowControllerImpl;']['applyVisible',prefix+'controller/IslandWindowState;'+('' if args.profile=='os5' else 'Ljava/lang/String;')][1]=='V'
print(f'PASS: {args.profile} landscape filter, force-show getter, window visibility signature and 2 static boolean fields.')
require('data/AtomIslandsDataReceiver', 'getInstance')
require('data/AtomIslandsDataReceiver', 'notifyAtomIsland', prefix+builder+';')
require('data/AtomIslandsDataReceiver', 'deleteAtomIsland', 'Ljava/lang/String;')
require('data/NewSuperXLoader', '<init>')
require('data/NewSuperXLoader', 'generateCardView', prefix+'view/CapsuleContentView;'+prefix+'data/AtomIslandsBean;')
require('view/CapsuleContentView', 'setContentView', 'Landroid/view/View;')
if args.profile!='os5':require('utils/IslandUtils', 'isClickable', prefix+'data/AtomIslandsBean;')
require('utils/IslandUtils', 'startPendingIntent', 'Landroid/content/Context;Landroid/app/PendingIntent;')
require('utils/IslandNotifyHelper', 'getInstance')
require('utils/IslandNotifyHelper', 'getIslandServiceListener')
require('utils/IslandNotifyHelper', 'getAbsContainerAnimator')
require('animation/AbsContainerAnimator', 'requestDisableAnimForNextFrame')
require('animation/ContainerAnimatorCommon', 'updateBlurAppearance', 'Z')
require('animation/ContainerAnimatorCommon', 'updateCapsuleBounds')
require('animation/ContainerAnimatorCommon', 'isAnimationRunning')
require('animation/ContainerAnimatorCommon', 'animateToTransition')
require('animation/ContainerAnimatorCommon', 'drawBounds', 'Landroid/graphics/Canvas;')
require('state/IslandAmbientState', 'disableNextAnimation')
require('view/CapsuleContentView', 'setVisibleAmount', 'F')
require('view/CapsuleContentView', 'getContentView')
require('music/RemoteMusicManager', 'getInstance')
if args.profile!='os5':require('IslandServiceListener', 'startPendingIntent', 'Landroid/app/PendingIntent;I')
require('IslandService', 'onCreate')
for name in ['generateLeftIslandView', 'generateRightIslandView']:
    require('data/NewSuperXLoader', name, prefix+'view/CapsuleContentView;'+prefix+'data/AtomIslandsBean;')
require('data/AtomIslandDisplayController', 'getInstance')
require('data/AtomIslandDisplayController', 'getMainIsland')
require('data/AtomIslandDisplayController', 'getCardIsland')
require('data/AtomIslandDisplayController', 'getSideIsland')
require('data/AtomIslandDisplayController', 'updateRealToShow', 'Ljava/util/ArrayList;')
require('state/DynamicCapsuleStateController', 'getInstance')
require('state/DynamicCapsuleStateController', 'getState')
require('data/AtomIslandsDataController', 'getInstance')
require('data/AtomIslandsDataController', 'handleIslandClickEvent', 'I')
require('data/AtomIslandsDataReceiver', 'removeAnimationMessage', prefix+'data/AtomIslandsBean;')
require('data/AtomIslandsBean', 'setTimeout', 'J')
require('view/CapsuleSwipeHelper', 'handleSwipe', 'Landroid/view/MotionEvent;')
require('view/CapsuleSwipeHelper', 'shouldDismissOnActionUp', public=False)
require('view/CapsuleSwipeHelper', 'shouldDragOutOnActionUp', public=False)
require('data/AtomIslandsDataController', 'handleSlideRemove', 'I'+prefix+'data/AtomIslandsBean;')
fix = 'Lcom/vivo/systemuiplugin/superx/common/utils/FixSuperXContextUtils;'
density_params='Landroid/content/Context;Landroid/content/res/Configuration;'+('Z' if args.profile=='os7' else '')
legacy_density=('getFixedDensityContext4Config','Landroid/content/Context;Landroid/content/res/Configuration;')
os5_contract=all((name,params) not in definitions[owner] for owner,name,params in [
    (prefix+builder+';','buildNotificationId','Ljava/lang/String;'),
    (prefix+builder+';','buildPermanent','Z'),
    (prefix+'IslandServiceListener;','startPendingIntent','Landroid/app/PendingIntent;I'),
])
selected_profile='os5' if os5_contract else 'os6' if legacy_density in definitions[fix] else 'os7'
assert selected_profile==args.profile,f'Density branch mismatch: expected {args.profile}, runtime selects {selected_profile}'
print(f'PASS: density branch selects {selected_profile}; the presence of a three-argument overload alone does not select OS7.')
for name, params in [('getInstance',''),('getFixedDensityContext4Config',density_params)]:
    flags, _ = definitions[fix][name, params]
    assert flags & 1
    checked += 1
proxy = 'Lcom/vivo/systemuiplugin/systemui/islands/IslandServiceProxy;'
for name in ['bindIslandService', 'getMIBinder', 'getMManager', 'getMBindSuccess', 'getMBindCount', 'init']:
    flags_proxy, _ = definitions[proxy][name, '']
    assert flags_proxy & 1, f'{name} must be public'
    checked += 1
print(f'PASS: {checked} native DEX method definitions match the {args.profile} adapter.')
for owner, name, expected in [
    ('animation/AbsContainerAnimator', 'mContainer', prefix+'view/DynamicCapsuleContainer;'),
    ('animation/ContainerAnimatorCommon', 'mIslandTransitionAnimator', 'Landroid/animation/ValueAnimator;'),
    ('view/CapsuleContentView', 'mParentContainer', prefix+'view/DynamicCapsuleContainer;'),
    ('view/DynamicCapsuleContainer', 'mCenterCutout', 'I'),
    ('view/DynamicCapsuleContainer', 'mCapsuleHeight', 'I'),
    ('view/CapsuleSwipeHelper', 'mTouchedBean', prefix+'data/AtomIslandsBean;'),
    ('view/CapsuleSwipeHelper', 'mIsDragging', 'I'),
    ('view/CapsuleSwipeHelper', 'mRealDistanceX', 'F'),
    ('view/CapsuleSwipeHelper', 'mRealDistanceY', 'F'),
    ('view/CapsuleSwipeHelper', 'mSlideAwaySlop', 'I'),
    ('view/CapsuleSwipeHelper', 'sSwipeAngle', 'I'),
    ('R$dimen', 'atom_is1and_left_or_right_image_round_size', 'I'),
    ('R$dimen', 'atom_is1and_left_or_right_image_width', 'I'),
    ('music/RemoteMusicManager', 'mMusicWidget', 'Landroid/view/View;'),
]:
    _, actual = field_definitions[prefix + owner + ';'][name]
    assert actual == expected, f'{owner}.{name}: expected {expected}, got {actual}'
print('PASS: 14 native DEX field definitions and types match the adapter.')
if args.profile in ['os5','os7']:
    assert field_definitions[prefix+'R$dimen;']['is1and_music_card_height'][1]=='I'
    print('PASS: native music card height resource field exists; '+('OS5 uses a separate content-safe height policy.' if args.profile=='os5' else 'OS7 retains the native height policy.'))
