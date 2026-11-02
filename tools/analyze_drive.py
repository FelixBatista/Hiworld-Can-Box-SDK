"""Replay one capture session using the production Java parser, never merging sessions."""
import argparse,collections,datetime,hashlib,json,pathlib,subprocess

def analyze(path, session=-1):
    root=pathlib.Path(__file__).resolve().parents[1]
    rows=[json.loads(s) for s in path.read_text(encoding='utf-8-sig').splitlines() if s.strip()]
    sessions=[]
    for r in rows:
        if r['type']=='reader_configuration':sessions.append([])
        if sessions:sessions[-1].append(r)
    rs=sessions[session]; start=rs[0]['timeMs']
    updates=[r for r in rs if r['type']=='update' and r.get('synthetic') is False]
    raw=[r for r in updates if (r['module'],r['code'])==(7,1019)]
    build=root/'build'/'replay';build.mkdir(parents=True,exist_ok=True)
    subprocess.run(['javac','--release','8','-Xlint:-options','-encoding','UTF-8','-d',str(build),str(root/'core/src/com/sleeperculture/teyes/CanboxStream.java'),str(root/'tools/ReplayFrames.java')],check=True)
    lines=[];profile=None
    for r in raw:
        if r.get('profile')!=profile:lines.append('RESET');profile=r.get('profile')
        lines.append(str(r['timeMs'])+'\t'+','.join(map(str,r.get('ints') or [])))
    run=subprocess.run(['java','-cp',str(build),'ReplayFrames'],input='\n'.join(lines)+'\n',text=True,capture_output=True,check=True)
    frames=[]
    for line in run.stdout.splitlines():
        t,h=line.split('\t');frames.append((int(t),list(bytes.fromhex(h))))
    bytype=collections.defaultdict(list)
    for t,a in frames:bytype[(bytes(a[:2]).hex(),a[3])].append((t,a))
    values=collections.defaultdict(list)
    for r in updates:
        if r['code']!=1019:values[(r['module'],r['code'])].append(r)
    signal_stats={}
    for (m,c),vs in values.items():
        unique=list(dict.fromkeys(json.dumps(v.get('ints')) for v in vs))
        if len(unique)>1 or (m,c) in [(0,4),(7,1000),(7,1031),(7,1032)]:
            signal_stats[f'{m}:{c}']={'count':len(vs),'unique':unique[:50]}
    types={}
    for (head,typ),vs in bytype.items():
        lengths=collections.Counter(len(a) for t,a in vs)
        variation={str(i):sorted(set(a[i] for t,a in vs if i<len(a)-1)) for i in range(4,max(lengths)-1)}
        types[f'{head}:{typ:02x}']={'count':len(vs),'lengths':dict(lengths),'values_by_offset':variation}
    psa=[(t,a) for t,a in frames if a[:4]==[90,165,12,26] and len(a)==17]
    def range_of(fn):
        vals=[fn(a) for t,a in psa]
        return {'min':min(vals),'max':max(vals),'unique':len(set(vals))} if vals else None
    result={'input_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'sessions_in_file':len(sessions),'selected_session_index':session%len(sessions),'start_utc':datetime.datetime.fromtimestamp(start/1000,datetime.timezone.utc).isoformat(),'duration_seconds':(max(r.get('timeMs',start) for r in rs)-start)/1000,'callbacks':len(updates),'raw_callbacks':len(raw),'validated_packets':len(frames),'parser_diagnostics':run.stderr.strip(),'capture_summary':[r for r in rs if r['type']=='capture_summary'],'signals':signal_stats,'types':types,'rpm':range_of(lambda a:a[13]*256+a[14]),'steering_raw':range_of(lambda a:int.from_bytes(bytes(a[10:12]),'big',signed=True)),'psa_sequence':[{'seconds':round((t-start)/1000,3),'packet':a} for t,a in psa]}
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('capture',type=pathlib.Path);p.add_argument('--session',type=int,default=-1);p.add_argument('--output',type=pathlib.Path,required=True);args=p.parse_args()
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(analyze(args.capture,args.session),indent=2),encoding='utf-8')
