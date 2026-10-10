#!/usr/bin/env python3
"""Verify exact defined DEX hook signatures in a user-supplied APK; no third-party libs.
This is not a behavioral analysis, APK signature check, or phone integration test.
"""
from __future__ import annotations
import argparse, hashlib, json, struct, zipfile
from pathlib import Path

EXPECTED_HASH = '10ae99c50aac4b9e758294dab7c7fc9895784ce7b964d69fed25ab15342f1466'
METHODS = set()
FIELDS = {'Lcom/vivo/gamedaemon/g;->b:Ljava/lang/String;'}
BASE = 'Lcom/vivo/gameassistant/inputbuttons/gameshoulder/'
DETAIL = BASE + 'entity/GameShoulderDetailsEntity;'
CONTRACT = BASE + 'IGameShoulderContract$'
def required(owner, name, args='', result='V'):
    METHODS.add(owner + '->' + name + '(' + args + ')' + result)
required('Lcom/vivo/gamedaemon/i0;', 'A', '', 'Ljava/util/List;')
required('Lcom/vivo/gamedaemon/g;', 't0', 'ILjava/lang/String;')
required('Lcom/vivo/common/a;', 'p', '', 'Lcom/vivo/common/a;')
required('Lcom/vivo/common/a;', 'O0', '', 'Z')
for name in ['p','q']: required(BASE+'utils/k;', name, 'Ljava/lang/String;', 'Z')
required(BASE+'utils/k;', 'b', DETAIL+'IIIZ', 'Ljava/lang/String;')
required(BASE+'utils/k;', 'r', 'Ljava/lang/String;')
required('Lcom/vivo/gameassistant/utils/e0;', 'c1', 'Ljava/lang/String;', 'Z')
required('Lcom/vivo/gameassistant/utils/e0;', 'V0', 'Landroid/content/Context;', 'Z')
required(BASE+'utils/l;', 'a', 'Landroid/content/Context;Z', 'Ljava/util/HashMap;')
for name,args in [('handleGameForeground','Z'),('m','IZ'),('h','Landroid/view/InputEvent;I'),
                  ('u',DETAIL),('closeGameShoulder',''),('p',''),('q','')]: required('Lbe/a;',name,args)
required('Lce/i0;', 'processPressAnim','Landroid/view/InputEvent;ZIZZI')
for view,names in [('GameShoulderSettingsView',['X0','c1']),('GameShoulderPreferencesView',['x0']),('GameShoulderUserGuideView',['A0'])]:
    for name in names: required(BASE+'view/'+view+';',name)
required(BASE+'view/GameShoulderSettingsView;', 't1', 'Landroid/view/InputEvent;ZIZZZ')
required('Lmc/b;', 'h', 'Landroid/content/Context;Landroid/multidisplay/MultiDisplayManager;Lcom/vivo/gameassistant/utils/u;ZZLjava/util/List;Ljava/util/List;')
required('Lnb/h;', 'l', 'IIIILcom/vivo/gameassistant/frameinterpolation/FrameInterpolationState;')
required(BASE+'a;', 'onGameSdkEvent', 'Lcom/vivo/gameassistant/entity/GameSDKEvent;')
ITEM='Lcom/vivo/gameassistant/homegui/sideslide/controlcenter/switches/model/'
required(ITEM+'j;', 'C', 'IF')
required(ITEM+'j;', 'n', '', ITEM+'QuickSwitchItemType;')
for owner,name,kind in [('Lbe/a;','d','Ljava/lang/String;'),('Lbe/a;','c','Landroid/content/Context;'),
    ('Lbe/a;','f',DETAIL),('Lce/i0;','b',CONTRACT+'IPresenter;'),
    (BASE+'view/GameShoulderSettingsView;','T','Lce/i0;'),(BASE+'view/GameShoulderSettingsView;','R','Ljava/lang/String;'),
    (BASE+'view/GameShoulderPreferencesView;','N',CONTRACT+'IMainView;'),
    (BASE+'view/GameShoulderUserGuideView;','A',CONTRACT+'IMainView;')]: FIELDS.add(owner+'->'+name+':'+kind)

# Offsets are byte offsets inside the frozen APK's DEX instruction arrays, audited against
# its native branches. These contracts do not execute Android callbacks or prove hardware effects.
C1='Lcom/vivo/gameassistant/utils/e0;->c1(Ljava/lang/String;)Z'
FORMAT=BASE+'utils/k;->b('+DETAIL+'IIIZ)Ljava/lang/String;'
DRIVER=BASE+'utils/k;->r(Ljava/lang/String;)V'
CALLS = [
    ('Lbe/a;->m(IZ)V',0x28,C1),
    ('Lbe/a;->m(IZ)V',0x68,FORMAT),
    ('Lbe/a;->m(IZ)V',0x96,FORMAT),
    ('Lbe/a;->m(IZ)V',0x9e,DRIVER),
    ('Lbe/a;->u('+DETAIL+')V',0x04,C1),
    (BASE+'utils/k;->q(Ljava/lang/String;)Z',0x58,C1),
    (BASE+'utils/k;->q(Ljava/lang/String;)Z',0xfe,'Lcom/vivo/common/a;->O0()Z'),
    (BASE+'utils/l;->a(Landroid/content/Context;Z)Ljava/util/HashMap;',0x10,BASE+'utils/k;->p(Ljava/lang/String;)Z'),
    (BASE+'utils/l;->a(Landroid/content/Context;Z)Ljava/util/HashMap;',0x30,C1),
    (BASE+'view/GameShoulderSettingsView;->t1(Landroid/view/InputEvent;ZIZZZ)V',0x1c,BASE+'utils/k;->p(Ljava/lang/String;)Z'),
    ('Lmc/b;->h(Landroid/content/Context;Landroid/multidisplay/MultiDisplayManager;Lcom/vivo/gameassistant/utils/u;ZZLjava/util/List;Ljava/util/List;)V',0x35c,C1),
    ('Lmc/b;->h(Landroid/content/Context;Landroid/multidisplay/MultiDisplayManager;Lcom/vivo/gameassistant/utils/u;ZZLjava/util/List;Ljava/util/List;)V',0x706,C1),
    ('Lbe/a;->handleGameForeground(Z)V',0x98,'Lbe/a;->closeGameShoulder()V'),
    ('Lbe/a;->closeGameShoulder()V',0x00,'Lbe/a;->unregisterGameCubeListener()V'),
    ('Lbe/a;->closeGameShoulder()V',0x3c,'Lbe/a;->q()V'),
    ('Lbe/a;->q()V',0x1c,'Lcom/vivo/common/utils/l;->n3(Landroid/content/Context;IZ)V'),
    ('Lbe/a;->q()V',0x26,'Lcom/vivo/common/utils/l;->n3(Landroid/content/Context;IZ)V'),
]

class Dex:
    def __init__(self, data: bytes):
        self.data=data
        if not data.startswith(b'dex\n') or len(data)<112: raise ValueError('Not a standard DEX file')
        ns,os=self.u4(56),self.u4(60)
        self.strings=[]
        for i in range(ns):
            start=self.u4(os+4*i);_,start=self.uleb(start)
            end=data.index(0,start)
            # Only ASCII class/type/method names are compared; malformed Unicode literals are not used.
            self.strings.append(data[start:end].decode('utf-8','replace'))
        nt,ot=self.u4(64),self.u4(68)
        self.types=[self.strings[self.u4(ot+4*i)] for i in range(nt)]
        self.protos=[]
        for i in range(self.u4(72)):
            off=self.u4(76)+i*12;ret=self.types[self.u4(off+4)];params=self.u4(off+8)
            args='' if params==0 else ''.join(self.types[self.u2(params+4+j*2)] for j in range(self.u4(params)))
            self.protos.append('('+args+')'+ret)
        self.fields=[]
        for i in range(self.u4(80)):
            off=self.u4(84)+8*i
            self.fields.append(self.types[self.u2(off)]+'->'+self.strings[self.u4(off+4)]+':'+self.types[self.u2(off+2)])
        self.methods=[]
        for i in range(self.u4(88)):
            off=self.u4(92)+8*i
            self.methods.append(self.types[self.u2(off)]+'->'+self.strings[self.u4(off+4)]+self.protos[self.u2(off+2)])
    def u4(self,off): return struct.unpack_from('<I',self.data,off)[0]
    def u2(self,off): return struct.unpack_from('<H',self.data,off)[0]
    def uleb(self,off):
        value=0
        for shift in range(0,35,7):
            byte=self.data[off];off+=1;value|=(byte&127)<<shift
            if byte<128:return value,off
        raise ValueError('Invalid ULEB128')
    def definitions(self):
        found=[]
        for i in range(self.u4(96)):
            off=self.u4(100)+i*32;cursor=self.u4(off+24)
            if not cursor: continue
            counts=[]
            for _ in range(4):n,cursor=self.uleb(cursor);counts.append(n)
            for group in range(4):
                index=0
                for _ in range(counts[group]):
                    delta,cursor=self.uleb(cursor);index+=delta
                    access,cursor=self.uleb(cursor)
                    if group<2:
                        sig=self.fields[index]
                        if sig in FIELDS:found.append({'kind':'field','signature':sig,'access':access})
                    else:
                        code,cursor=self.uleb(cursor);sig=self.methods[index]
                        if sig in METHODS:found.append({'kind':'method','signature':sig,'access':access,'code_offset':code,'has_code':code>0,
                            'instruction_sha256':hashlib.sha256(self.data[code+16:code+16+self.u4(code+12)*2]).hexdigest() if code else None})
        return found

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();sha=hashlib.sha256(args.apk.read_bytes()).hexdigest();records=[];contracts=[]
    with zipfile.ZipFile(args.apk) as apk:
        dex_files=sorted(n for n in apk.namelist() if n.startswith('classes') and n.endswith('.dex') and '/' not in n)
        for name in dex_files:
            dex=Dex(apk.read(name));definitions=dex.definitions()
            for row in definitions:records.append(dict(dex=name,**row))
            methods={r['signature']:r for r in definitions if r['kind']=='method' and r['has_code']}
            for signature,offset,target in CALLS:
                if signature not in methods:continue
                pos=methods[signature]['code_offset']+16+offset
                opcode=dex.data[pos];method_index=dex.u2(pos+2)
                actual=dex.methods[method_index] if method_index<len(dex.methods) else None
                contracts.append({'method':signature,'offset':hex(offset),'expected_call':target,
                    'actual_call':actual,'pass':opcode in {0x6e,0x6f,0x70,0x71,0x72,0x74,0x75,0x76,0x77,0x78} and actual==target})
            # The false m-category branch uses existing f and the exact generic formatter.
            signature='Lbe/a;->m(IZ)V'
            if signature in methods:
                pos=methods[signature]['code_offset']+16
                jump=struct.unpack_from('<h',dex.data,pos+0x32)[0]*2+0x30
                field_index=dex.u2(pos+0x74)
                contracts.append({'method':signature,'branch':'c1 false -> existing native Details',
                    'pass':dex.data[pos+0x30]==0x38 and jump==0x72 and dex.data[pos+0x72]==0x54
                      and dex.fields[field_index]=='Lbe/a;->f:'+DETAIL})
            signature=BASE+'utils/l;->a(Landroid/content/Context;Z)Ljava/util/HashMap;'
            if signature in methods:
                pos=methods[signature]['code_offset']+16
                jump=struct.unpack_from('<h',dex.data,pos+0x1a)[0]*2+0x18
                contracts.append({'method':signature,'branch':'k.p true -> seven native menu labels',
                    'pass':dex.data[pos+0x18]==0x39 and jump==0x78
                      and dex.data[pos+0x104]==0x25 and dex.data[pos+0x105]==7})
    present={r['signature'] for r in records if r['kind']=='field' or r.get('has_code')}
    missing=sorted((METHODS|FIELDS)-present)
    result={'apk_name':args.apk.name,'sha256':sha,'expected_sha256_match':sha==EXPECTED_HASH,
            'dex_count':len(dex_files),'required_definitions':len(METHODS|FIELDS),'found':records,'missing':missing,
            'native_branch_contracts':contracts,'native_contracts_expected':len(CALLS)+2,
            'pass':sha==EXPECTED_HASH and not missing and len(contracts)==len(CALLS)+2 and all(c['pass'] for c in contracts),
            'scope':'Frozen uploaded APK bytes, exact defined methods/fields and 19 native branch/call contracts; no Android/device behavior verified.'}
    text=json.dumps(result,ensure_ascii=False,indent=2)
    if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text+'\n',encoding='utf-8')
    print(text)
    raise SystemExit(0 if result['pass'] else 1)
if __name__=='__main__': main()
