#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise actual isolated HTTP/MySQL workflows using TEST records and private random credentials."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8129').rstrip('/');checks=0

def check(ok,message):
    """Count real acceptance checks without printing credentials or business responses."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

def key():return str(uuid.uuid4())

class Client:
    """Use an isolated session cookie jar and the application's real CSRF endpoint."""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,path+': wrong error')
        return value

def detail(kind,id):return admin.request(f'/{kind}/{id}')
def record(kind,id):return detail(kind,id)['record']
def execution(run,id):return next(e for e in detail('runs',run)['executions'] if e['id']==id)
def command(who,kind,id,action,run=None,extra=None,status=200,code=None,body=None):
    """Bind a fresh version and stable request key to one explicit evidence action."""
    row=execution(run,id) if kind=='executions' else next(h for h in detail('runs',run)['holds'] if h['id']==id) if kind=='holds' else record(kind,id)
    value=body or {'requestKey':key(),'version':row['version'],'note':'TEST 已核实的人工台账凭据'}
    value.update(extra or {});return who.request(f'/{kind}/{id}/commands/{action}','POST',value,status,code)

def capture():
    """Capture private stable responses for exact restart and restored-volume comparison."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard']
    for kind in ['productions','books','runs']:
        paths.append('/'+kind+'?size=100&sort=oldest')
        paths.extend(f'/{kind}/{r["id"]}' for r in admin.request('/'+kind+'?size=100')['content'])
    return {p:admin.request(p) for p in paths}

parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--capture',action='store_true');args=parser.parse_args()
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    state=json.loads(STATE.read_text());current=capture()
    if args.capture:
        state['responses']=current;STATE.write_text(json.dumps(state,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(current),'result':'PASS'}));raise SystemExit
    for p,expected in state['responses'].items():check(current[p]==expected,'Persistence mismatch: '+p)
    for name,username in state['users'].items():check(Client(username,state['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(current),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable isolated database.')
if STATE.exists():raise SystemExit('QA state exists; use --verify or a new isolated database.')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};userIds={}
roles={r['name']:r['id'] for r in admin.request('/admin/roles')};dep=admin.request('/admin/departments','POST',{'name':'TEST 舞台协同 '+suffix})['id'];outsideDep=admin.request('/admin/departments','POST',{'name':'TEST 外部制作 '+suffix})['id']
allRole=admin.request('/admin/roles','POST',{'name':'TEST 操作员ALL '+suffix,'scope':'ALL','permissions':['run.read','cue.operate','run.hold','dashboard','export']})['id']
selfRole=admin.request('/admin/roles','POST',{'name':'TEST 本人制作 '+suffix,'scope':'SELF','permissions':['production.read','production.write','book.read','book.write','run.read','run.write','dashboard','export']})['id']
for name,role,department in [('ops',roles['制作协调'],dep),('review',roles['独立监督'],dep),('caller',roles['提示员'],dep),('operator',roles['技术操作员'],dep),('other',allRole,dep),('outside',roles['制作协调'],outsideDep),('self',selfRole,dep)]:
    username='test-'+name+'-'+suffix;users[name]=username;userIds[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+{'ops':'制作协调','review':'独立监督','caller':'场次提示员','operator':'灯光操作员','other':'视频操作员ALL','outside':'其他制作部门','self':'本人制作'}[name],'password':password,'roleId':role,'departmentId':department,'enabled':True})['id'];clients[name]=Client(username,password)
ops,review,caller,operator,other=[clients[n] for n in ['ops','review','caller','operator','other']]
p=ops.request('/productions','POST',{'requestKey':key(),'reference':'TEST-'+suffix,'name':'TEST 剧场排练 '+suffix,'departmentId':dep,'enabled':True});pid=p['id']
books=[];runs=[]
def book():
    b=ops.request('/books','POST',{'requestKey':key(),'productionId':pid,'title':'TEST 演出提示本 '+suffix});books.append(b['id']);return b['id']
def cue(book,seq,optional,kind):
    return ops.request('/cues','POST',{'requestKey':key(),'bookId':book,'sequence':seq,'code':kind+str(seq),'kind':kind,'triggerText':'TEST 幕前动作标记 '+str(seq),'description':'TEST 经人工核实后记录本条执行','optional':optional,'enabled':True})['id']
def approve(id):command(ops,'books',id,'submit');command(review,'books',id,'approve')
def run(book,mode='REHEARSAL'):
    r=ops.request('/runs','POST',{'requestKey':key(),'reference':'TEST-RUN-'+key()[:8],'bookId':book,'callerId':userIds['caller'],'supervisorId':userIds['review'],'location':'TEST 小剧场','plannedAt':datetime.now(timezone.utc).isoformat(),'mode':mode});runs.append(r['id']);return r['id']
def prepare(id):
    es=detail('runs',id)['executions'];a,b=[e['id'] for e in es]
    for eid,actor,name in [(a,operator,'operator'),(b,other,'other')]:command(ops,'executions',eid,'assign',id,{'operatorId':userIds[name]});command(actor,'executions',eid,'receive',id)
    return a,b
b1=book();c1=cue(b1,1,False,'LIGHT');c2=cue(b1,2,True,'VIDEO');approve(b1)
r1=run(b1,'PERFORMANCE');r2=run(b1);r3=run(b1);r4=run(b1)
a,z=prepare(r1);command(caller,'runs',r1,'start');command(caller,'executions',z,'standby',r1,status=409,code='CUE_OUT_OF_ORDER');command(caller,'executions',a,'skip',r1,status=409,code='MANDATORY_CUE_CANNOT_SKIP');command(caller,'runs',r1,'end',status=409,code='UNFINISHED_CUES')
# Exact responses persist for valid retries even after the cue has progressed.
body={'requestKey':key(),'version':execution(r1,a)['version'],'note':'TEST 灯光待命记录'};path=f'/executions/{a}/commands/standby';first=caller.request(path,'POST',body);command(operator,'executions',a,'ready',r1);check(caller.request(path,'POST',body)==first,'Retry changed response');caller.request(path,'POST',{**body,'note':'TEST mismatched request'},409,'REQUEST_KEY_REUSED');admin.request(path,'POST',body,403,'ASSIGNED_CALLER_REQUIRED')
# A held READY cue cannot be called; independent resolution followed by resume resets it.
command(operator,'runs',r1,'hold');command(caller,'executions',a,'call',r1,status=409,code='INVALID_STATE');command(caller,'runs',r1,'resume',status=409,code='OPEN_HOLD');hid=detail('runs',r1)['holds'][0]['id'];command(admin,'holds',hid,'resolve',r1,status=403,code='ASSIGNED_SUPERVISOR_REQUIRED');command(review,'holds',hid,'resolve',r1);command(caller,'runs',r1,'resume');check(execution(r1,a)['status']=='PENDING' and execution(r1,a)['readyAt'] is None,'Hold resume retained readiness');command(caller,'executions',a,'call',r1,status=409,code='INVALID_STATE');command(caller,'executions',a,'standby',r1);command(operator,'executions',a,'ready',r1)
# Race two fresh commands at the same version, only one changes the cue.
version=execution(r1,a)['version']
def competing():
    return Client(users['caller'],password).request(f'/executions/{a}/commands/call','POST',{'requestKey':key(),'version':version,'note':'TEST concurrent cue call'},(200,409))
with concurrent.futures.ThreadPoolExecutor(2) as pool:one=pool.submit(competing);two=pool.submit(competing);results=[one.result(),two.result()]
check(sum('id' in v for v in results)==1,'Concurrent cue recorded twice');command(caller,'runs',r1,'abort',status=409,code='EXECUTION_STILL_OPEN');command(caller,'runs',r1,'hold');command(operator,'executions',a,'done',r1);hid=detail('runs',r1)['holds'][-1]['id'];command(review,'holds',hid,'resolve',r1);command(caller,'runs',r1,'resume');command(caller,'executions',z,'skip',r1);command(caller,'runs',r1,'end');command(admin,'runs',r1,'close',status=403,code='ASSIGNED_SUPERVISOR_REQUIRED');command(review,'runs',r1,'close');check(record('runs',r1)['status']=='CLOSED' and record('runs',r1)['outcome']=='FINISHED','Full run not reviewed')
# Frozen source, copy-on-revision and older run snapshots remain independent.
ops.request(f'/cues/{c1}','PUT',{'requestKey':key(),'version':1,'bookId':b1},409,'INVALID_STATE');b2=book();check(len(detail('books',b2)['cues'])==2,'New revision omitted source cues');edit=detail('books',b2)['cues'][0];ops.request('/cues/'+str(edit['id']),'PUT',{**edit,'requestKey':key(),'triggerText':'TEST 新版不同动作标记'});approve(b2);ops.request('/runs','POST',{'requestKey':key(),'reference':'TEST-'+key()[:8],'bookId':b1,'callerId':userIds['caller'],'supervisorId':userIds['review'],'location':'TEST','plannedAt':datetime.now(timezone.utc).isoformat(),'mode':'REHEARSAL'},409,'SUPERSEDED_BOOK');check(record('runs',r1)['bookRevision']==1,'Frozen run revision changed');check(detail('runs',r1)['executions'][0]['triggerText']!='TEST 新版不同动作标记','Frozen cue overwritten')
# Assigned ALL operator sees only own cues and cannot discover other runs.
visible=other.request(f'/runs/{r1}');check([e['id'] for e in visible['executions']]==[z],'Operator ALL leaked other cues');check(other.request('/runs?size=100')['total']==1,'Operator ALL leaked other runs');other.request(f'/runs/{r2}',status=403,code='OUT_OF_SCOPE');other.request(f'/executions/{a}/commands/done','POST',{'requestKey':key(),'version':execution(r1,a)['version'],'note':'TEST'},403,'OUT_OF_SCOPE');other.request('/admin/users',status=403,code='FORBIDDEN');other.request('/audit',status=403,code='FORBIDDEN');other.request('/books',status=403,code='FORBIDDEN');check('zhuatech' not in json.dumps(other.request(f'/runs/{r1}/report.json')),'Advertisement in business export')
for name in ['outside','self']:
    clients[name].request(f'/runs/{r1}',status=403,code='OUT_OF_SCOPE');clients[name].request(f'/runs/{r1}/report.json',status=403,code='OUT_OF_SCOPE');check(clients[name].request('/runs?size=100')['total']==0,'Scope leak '+name)
# Prepare receipt invalidation, declined revision, cancelled and aborted outcomes.
a2,z2=prepare(r2);command(ops,'executions',a2,'assign',r2,{'operatorId':userIds['other']});command(caller,'runs',r2,'start',status=409,code='CREW_NOT_READY');command(other,'executions',a2,'receive',r2);command(caller,'runs',r2,'start');command(caller,'runs',r2,'abort');command(review,'runs',r2,'close');check(record('runs',r2)['outcome']=='ABORTED','Aborted outcome lost');command(caller,'runs',r3,'cancel');command(caller,'runs',r4,'start',status=409,code='CREW_NOT_READY')
b3=book();command(ops,'books',b3,'submit');command(review,'books',b3,'reject');command(ops,'books',b3,'cancel')
# Same book creator as authorized reviewer still may not approve current edits.
b4=book();original=record('books',b4);admin.request('/books/'+str(b4),'PUT',{'requestKey':key(),'version':original['version'],'productionId':pid,'title':'TEST 管理员参与修订'});command(ops,'books',b4,'submit');command(admin,'books',b4,'approve',status=409,code='INDEPENDENT_REVIEW_REQUIRED');command(review,'books',b4,'approve');r5=run(b4);prepare(r5)
# MySQL TIMESTAMP bounds and nanosecond input preserve the successful original response.
policy={'requestKey':key(),'reference':'TEST-NANO-'+suffix,'bookId':b4,'callerId':userIds['caller'],'supervisorId':userIds['review'],'location':'TEST 时间精度检查','plannedAt':'2040-01-01T00:00:00Z','mode':'REHEARSAL'}
ops.request('/runs','POST',policy,400,'INVALID_TIME');policy['plannedAt']='2026-10-06T12:00:00.123456789Z';nano=ops.request('/runs','POST',policy);runs.append(nano['id']);check(nano['plannedAt']=='2026-10-06T12:00:00.123456Z','Nanoseconds not normalized');check(record('runs',nano['id'])==nano,'First response differed after MySQL persistence')
# Database FK protection, settings validation, real CSRF, immediate account disable.
admin.request('/admin/departments/'+str(dep),'DELETE',{},409,'CONFLICT');ops.request('/runs?size=101',status=400,code='INVALID_INPUT');ops.request('/runs?sort=sql',status=400,code='INVALID_INPUT');ops.request('/runs','POST',{},403,csrf=False);check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Password hash leak');check(len(admin.request('/admin/permissions'))==15 and len(admin.request('/admin/menus'))==12,'Catalog registration mismatch')
account=next(a for a in admin.request('/admin/users') if a['id']==userIds['outside']);admin.request('/admin/users/'+str(account['id']),'PUT',{**account,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(account['id']),'PUT',{**account,'enabled':True})
check(operator.request('/dashboard')['cueTotal']==2,'Operator dashboard does not use own assignments')
STATE.parent.mkdir(exist_ok=True);state={'password':password,'users':users,'userIds':userIds,'departmentId':dep,'productionId':pid,'bookIds':books,'runIds':runs,'uiRunReference':record('runs',r5)['reference']};state['responses']=capture()
fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'books':len(books),'runs':len(runs),'result':'PASS'}))
