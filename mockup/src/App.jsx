import { useState, useRef, useEffect, useMemo } from 'react'
import { Phone, MessageCircle, Plus, Camera, Image as ImageIcon, FileText, FileSpreadsheet, FileType, Upload, Search, Trash2, Star, Edit2, X, Download, Settings, BarChart3, Filter, MoreVertical, Check } from 'lucide-react'
import Tesseract from 'tesseract.js'
import * as pdfjsLib from 'pdfjs-dist'
import * as XLSX from 'xlsx'
import mammoth from 'mammoth'
import Papa from 'papaparse'

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()

const STATUSES = ['new','called','interested','callback','notinterested','voicemail']
const STATUS_LABEL = { new:'New', called:'Called', interested:'Interested', callback:'Callback', notinterested:'Not interested', voicemail:'Voicemail' }
const STATUS_CLASS = { new:'status-new', called:'status-called', interested:'status-interested', callback:'status-callback', notinterested:'status-notinterested', voicemail:'status-voicemail' }

const phoneRegex = /(\+?\d[\d\s\-().]{6,}\d)/
function extractPhone(s){ const m=s.match(phoneRegex); return m?m[0].replace(/\s+/g,' ').trim():null }
function cleanPhone(p){ return p.replace(/[^\d+]/g,'') }
function parseTextToContacts(text){
  const contacts=[]; const seen=new Set()
  for(const raw of text.split(/\r?\n/)){
    const line=raw.trim(); if(!line) continue
    let name='', phone=''
    const delims=['\t',',',';','|',' - ',' – ',':','  ']
    const d=delims.find(x=>line.includes(x))
    if(d){
      const parts=line.split(d); const a=parts[0].trim(); const b=parts.slice(1).join(' ').trim()
      if(phoneRegex.test(a)&&!phoneRegex.test(b)){ phone=a; name=b }
      else if(phoneRegex.test(b)){ phone=extractPhone(b)||''; name=a }
      else { phone=extractPhone(line)||''; name=line.replace(phoneRegex,'').replace(/[-:,]/g,'').trim() }
    } else { phone=extractPhone(line)||''; name=phone?line.replace(phoneRegex,'').replace(/[-:,]/g,'').trim():line }
    if(phone){
      const p=extractPhone(phone)||phone; const k=cleanPhone(p)
      if(!seen.has(k)){ seen.add(k); contacts.push({id:crypto.randomUUID(), name:name||'Unknown', phone:p, status:'new', notes:'', favorite:false, callCount:0, createdAt:Date.now(), calledAt:null}) }
    }
  }
  return contacts
}

const LS_KEY='quickcall_contacts_v2'
const LS_THEME='quickcall_theme'

export default function App(){
  const [theme, setTheme] = useState(()=>localStorage.getItem(LS_THEME)||'light')
  const [accent, setAccent] = useState(()=>localStorage.getItem('quickcall_accent')||'#2563eb')
  const [contacts, setContacts] = useState(()=>{ try{ return JSON.parse(localStorage.getItem(LS_KEY))||[] }catch{return[]} })
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const [sort, setSort] = useState('date')
  const [showImport, setShowImport] = useState(false)
  const [showManual, setShowManual] = useState(false)
  const [showEdit, setShowEdit] = useState(null)
  const [showSettings, setShowSettings] = useState(false)
  const [showStats, setShowStats] = useState(false)
  const [manualName, setManualName] = useState('')
  const [manualPhone, setManualPhone] = useState('')
  const [manualNotes, setManualNotes] = useState('')
  const [status, setStatus] = useState('')
  const [processing, setProcessing] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const [batchMode, setBatchMode] = useState(false)
  const [selected, setSelected] = useState(new Set())
  const [settings, setSettings] = useState(()=>{ try{ return JSON.parse(localStorage.getItem('quickcall_settings'))||{countryCode:'+91', waTemplate:'Hi {name}, this is a quick call regarding...', defaultStatus:'new', cardStyle:'comfortable', hideWhatsApp:false, tapToCall:true, showNotes:true, showStatus:true, showCallCount:true, showLastCalled:true, showSms:false} }catch{return {countryCode:'+91', waTemplate:'Hi {name}, this is a quick call regarding...', defaultStatus:'new', cardStyle:'comfortable', hideWhatsApp:false, tapToCall:true, showNotes:true, showStatus:true, showCallCount:true, showLastCalled:true, showSms:false}} })
  const fileInputRef=useRef(null); const cameraInputRef=useRef(null); const genericInputRef=useRef(null)

  useEffect(()=>{ document.documentElement.setAttribute('data-theme',theme); localStorage.setItem(LS_THEME,theme) },[theme])
  useEffect(()=>{ localStorage.setItem('quickcall_accent',accent) },[accent])
  useEffect(()=>{ localStorage.setItem(LS_KEY,JSON.stringify(contacts)) },[contacts])
  useEffect(()=>{ localStorage.setItem('quickcall_settings',JSON.stringify(settings)) },[settings])

  const toast = (m)=>{ setStatus(m); setTimeout(()=>setStatus(''),2500) }

  const addContacts = (newOnes) => {
    setContacts(prev=>{
      const seen=new Set(prev.map(c=>cleanPhone(c.phone)))
      const toAdd=newOnes.filter(c=>{ const k=cleanPhone(c.phone); if(seen.has(k)) return false; seen.add(k); return true })
      return [...prev, ...toAdd]
    })
    toast(`Imported ${newOnes.length} contact(s)`)
  }

  const filtered = useMemo(()=>{
    let list = contacts.filter(c=>{
      const q=query.toLowerCase()
      const matchQ = c.name.toLowerCase().includes(q) || c.phone.includes(q) || (c.notes||'').toLowerCase().includes(q)
      const matchF = filter==='all' ? true :
        filter==='favorites' ? c.favorite :
        filter==='called' ? c.status==='called' :
        c.status===filter
      return matchQ && matchF
    })
    list = [...list].sort((a,b)=>{
      if(sort==='name') return a.name.localeCompare(b.name)
      if(sort==='status') return a.status.localeCompare(b.status)
      return b.createdAt - a.createdAt
    })
    return list
  },[contacts,query,filter,sort])

  const stats = useMemo(()=>({
    total: contacts.length,
    called: contacts.filter(c=>c.status==='called').length,
    interested: contacts.filter(c=>c.status==='interested').length,
    pending: contacts.filter(c=>c.status==='new').length,
    callback: contacts.filter(c=>c.status==='callback').length,
  }),[contacts])

  const handleCall = (c) => {
    setContacts(prev=>prev.map(x=>x.id===c.id?{...x,status:'called',calledAt:Date.now(),callCount:(x.callCount||0)+1}:x))
    window.location.href = `tel:${cleanPhone(c.phone)}`
  }
  const handleWhatsApp = (c) => {
    const n=cleanPhone(c.phone).replace(/^\+/,'')
    const msg = (settings.waTemplate||'').replace('{name}',c.name)
    window.open(`https://wa.me/${n}?text=${encodeURIComponent(msg)}`,'_blank')
  }
  const toggleFav = (id) => setContacts(prev=>prev.map(c=>c.id===id?{...c,favorite:!c.favorite}:c))
  const setStatus2 = (id,st) => setContacts(prev=>prev.map(c=>c.id===id?{...c,status:st}:c))
  const remove = (id) => setContacts(prev=>prev.filter(c=>c.id!==id))
  const updateContact = (c) => { setContacts(prev=>prev.map(x=>x.id===c.id?c:x)); setShowEdit(null); toast('Updated') }

  const processText = (text) => {
    const c = parseTextToContacts(text)
    if(c.length) addContacts(c); else toast('No contacts found')
  }

  const handleFile = async (file) => {
    if(!file) return
    setProcessing(true); toast('Processing...')
    try {
      const name=file.name.toLowerCase()
      if(/\.(png|jpe?g|bmp|webp|gif)$/i.test(name)){
        const {data}=await Tesseract.recognize(file,'eng'); processText(data.text)
      } else if(name.endsWith('.pdf')){
        const buf=await file.arrayBuffer(); const pdf=await pdfjsLib.getDocument({data:buf}).promise
        let text=''
        for(let i=1;i<=pdf.numPages;i++){ const page=await pdf.getPage(i); const content=await page.getTextContent(); text+=content.items.map(it=>it.str).join(' ')+'\n' }
        processText(text)
      } else if(/\.(xlsx|xls|csv)$/i.test(name)){
        const buf=await file.arrayBuffer(); const wb=XLSX.read(buf,{type:'array'}); const ws=wb.Sheets[wb.SheetNames[0]]; const csv=XLSX.utils.sheet_to_csv(ws)
        const parsed=Papa.parse(csv,{header:true,skipEmptyLines:true})
        let contacts=[]
        if(parsed.data&&parsed.data.length&&parsed.meta.fields){
          const fields=parsed.meta.fields.map(f=>f.toLowerCase())
          const nameIdx=fields.findIndex(f=>/name|contact|person/i.test(f))
          const phoneIdx=fields.findIndex(f=>/phone|mobile|number|tel|cell/i.test(f))
          if(phoneIdx>=0){
            for(const row of parsed.data){
              const vals=Object.values(row)
              const phone=phoneIdx>=0?vals[phoneIdx]:''
              const nm=nameIdx>=0?vals[nameIdx]:''
              if(phone) contacts.push({id:crypto.randomUUID(),name:nm||'Unknown',phone:String(phone),status:settings.defaultStatus||'new',notes:'',favorite:false,callCount:0,createdAt:Date.now(),calledAt:null})
            }
          }
        }
        if(contacts.length===0){ const c=parseTextToContacts(csv); contacts=c }
        if(contacts.length) addContacts(contacts); else toast('No contacts found')
      } else if(name.endsWith('.docx')){
        const buf=await file.arrayBuffer(); const res=await mammoth.extractRawText({arrayBuffer:buf}); processText(res.value)
      } else {
        const text=await file.text(); processText(text)
      }
    } catch(e){ console.error(e); toast('Error: '+(e.message||e)) }
    finally { setProcessing(false); setShowImport(false) }
  }

  const triggerInput=(ref)=>ref.current&&ref.current.click()

  const exportCSV = () => {
    const rows=[['Name','Phone','Status','Notes','Favorite','Calls','Created']]
    contacts.forEach(c=>rows.push([c.name,c.phone,c.status,c.notes||'',c.favorite?'Yes':'No',c.callCount||0,new Date(c.createdAt).toLocaleString()]))
    const csv=rows.map(r=>r.map(v=>`"${String(v).replace(/"/g,'""')}"`).join(',')).join('\n')
    const blob=new Blob([csv],{type:'text/csv'}); const url=URL.createObjectURL(blob)
    const a=document.createElement('a'); a.href=url; a.download='quickcall_export.csv'; a.click(); URL.revokeObjectURL(url)
    toast('Exported')
  }

  const clearAll = () => { if(confirm('Delete all contacts?')){ setContacts([]); toast('Cleared') } }

  const batchDelete = () => {
    setContacts(prev=>prev.filter(c=>!selected.has(c.id)))
    setSelected(new Set()); setBatchMode(false); toast('Deleted selected')
  }

  const toggleSelect = (id) => {
    setSelected(prev=>{ const n=new Set(prev); if(n.has(id)) n.delete(id); else n.add(id); return n })
  }

  return (
    <div className="phone" data-theme={theme} style={{'--primary':accent,'--primary-dark':accent}} onDragOver={e=>{e.preventDefault();setDragOver(true)}} onDragLeave={()=>setDragOver(false)} onDrop={e=>{e.preventDefault();setDragOver(false);const f=e.dataTransfer.files?.[0];if(f)handleFile(f)}}>
      <div className="header">
        <div style={{display:'flex',alignItems:'center',gap:10}}>
          <svg width="34" height="34" viewBox="0 0 34 34" style={{flexShrink:0}}>
            <rect width="34" height="34" rx="10" fill="rgba(255,255,255,.25)"/>
            <path d="M9 11c0-1.1.9-2 2-2h2.5l1.5 4-2 1.5a8 8 0 004 4l1.5-2 4 1.5V21a2 2 0 01-2 2A13 13 0 019 10z" fill="#fff"/>
          </svg>
          <div>
            <h1>Quick Call</h1>
            <p>{contacts.length} contacts · {stats.pending} pending</p>
          </div>
        </div>
        <div style={{display:'flex',gap:6}}>
          <button className="iconbtn" onClick={()=>{ if(confirm('Delete ALL contacts?')){ setContacts([]); toast('All deleted') } }} title="Delete all">🗑️</button>
          <button className="iconbtn" onClick={()=>setShowStats(true)} title="Stats"><BarChart3 size={18}/></button>
          <button className="iconbtn" onClick={()=>setTheme(t=>t==='light'?'dark':t==='dark'?'amoled':'light')} title="Theme">{theme==='light'?'🌙':theme==='dark'?'⬛':'☀️'}</button>
          <button className="iconbtn" onClick={()=>setShowSettings(true)} title="Settings"><Settings size={18}/></button>
        </div>
      </div>

      <div className="toolbar">
        <input className="search" placeholder="Search name, number or notes..." value={query} onChange={e=>setQuery(e.target.value)} />
        <select value={sort} onChange={e=>setSort(e.target.value)} style={{padding:'10px',border:'1px solid var(--border)',borderRadius:10,background:'var(--surface2)',color:'var(--text)'}}>
          <option value="date">Newest</option>
          <option value="name">Name</option>
          <option value="status">Status</option>
        </select>
      </div>

      <div className="stats">
        <div className="stat"><div className="n">{stats.total}</div><div className="l">Total</div></div>
        <div className="stat"><div className="n" style={{color:'var(--green)'}}>{stats.called}</div><div className="l">Called</div></div>
        <div className="stat"><div className="n" style={{color:'var(--amber)'}}>{stats.interested}</div><div className="l">Interested</div></div>
        <div className="stat"><div className="n" style={{color:'var(--primary)'}}>{stats.pending}</div><div className="l">Pending</div></div>
      </div>

      <div className="filters">
        {['all','new','called','interested','callback','notinterested','voicemail','favorites'].map(f=>(
          <div key={f} className={`chip ${filter===f?'active':''}`} onClick={()=>setFilter(f)}>
            {f==='all'?'All':f==='favorites'?'★ Fav':STATUS_LABEL[f]||f}
          </div>
        ))}
      </div>

      <div className="importcard">
        <h3>📥 Import Contacts</h3>
        <div className="importgrid">
          {[
            {label:'Camera',color:'#7c3aed',icon:<Camera size={18}/>,ref:cameraInputRef,accept:'image/*',capture:'environment'},
            {label:'Image',color:'#2563eb',icon:<ImageIcon size={18}/>,ref:fileInputRef,accept:'image/*'},
            {label:'PDF',color:'#dc2626',icon:<FileText size={18}/>,ref:genericInputRef,accept:'.pdf,application/pdf'},
            {label:'Excel',color:'#16a34a',icon:<FileSpreadsheet size={18}/>,ref:genericInputRef,accept:'.xlsx,.xls,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'},
            {label:'Word',color:'#0d9488',icon:<FileType size={18}/>,ref:genericInputRef,accept:'.docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document'},
            {label:'Any file',color:'#64748b',icon:<Upload size={18}/>,ref:genericInputRef,accept:'*/*'},
          ].map((o,i)=>(
            <div key={i} className="importbtn" onClick={()=>triggerInput(o.ref)}>
              <div className="ic" style={{background:o.color}}>{o.icon}</div>
              {o.label}
            </div>
          ))}
        </div>
      </div>

      <div style={{padding:'0 14px 8px',display:'flex',gap:8,alignItems:'center'}}>
        <button className="btn btn-gray btn-sm" onClick={()=>setBatchMode(!batchMode)}>{batchMode?'Exit select':'Select'}</button>
        {batchMode && selected.size>0 && <button className="btn btn-danger btn-sm" onClick={batchDelete}>Delete ({selected.size})</button>}
        <button className="btn btn-gray btn-sm" onClick={exportCSV}><Download size={14}/> Export</button>
        <button className="btn btn-gray btn-sm" onClick={()=>setShowManual(true)}><Plus size={14}/> Add</button>
      </div>

      <div className="list">
        {filtered.length===0 && (
          <div className="empty">
            <h3>No contacts found</h3>
            <p>Import a list above or add manually</p>
          </div>
        )}
        {filtered.map(c=>(
          <div className={`card ${settings.cardStyle==='compact'?'compact':''} ${selected.has(c.id)?'sel':''}`} key={c.id} onClick={()=>{ if(settings.tapToCall && !batchMode) handleCall(c) }} style={{cursor: settings.tapToCall?'pointer':'default'}}>
            <div className="top">
              {batchMode && <input type="checkbox" checked={selected.has(c.id)} onChange={()=>toggleSelect(c.id)} style={{width:18,height:18}} />}
              <div className="name">{c.name}</div>
              <button className="fav" onClick={()=>toggleFav(c.id)} title="Favorite">{c.favorite?'★':'☆'}</button>
              <button className="btn btn-gray btn-sm" onClick={()=>setShowEdit(c)} title="Edit"><Edit2 size={13}/></button>
            </div>
            <div className="phone">{c.phone}</div>
            <div className="meta">
              {settings.showStatus && <span className={`chip2 ${STATUS_CLASS[c.status]}`}>{STATUS_LABEL[c.status]}</span>}
              {settings.showCallCount && c.callCount>0 && <span className="chip2" style={{background:'var(--surface2)',color:'var(--muted)'}}>📞 {c.callCount}</span>}
              {settings.showLastCalled && c.calledAt && <span className="chip2" style={{background:'var(--surface2)',color:'var(--muted)'}}>{new Date(c.calledAt).toLocaleDateString()}</span>}
            </div>
            {settings.showNotes && c.notes && <div className="notes">"{c.notes}"</div>}
            <div className="actions" onClick={e=>e.stopPropagation()}>
              <button className="btn btn-green" onClick={()=>handleCall(c)}><Phone size={16}/> Call</button>
              {!settings.hideWhatsApp && <button className="btn btn-teal" onClick={()=>handleWhatsApp(c)}><MessageCircle size={16}/> WhatsApp</button>}
              {settings.showSms && <button className="btn btn-amber" onClick={()=>{ const n=cleanPhone(c.phone); window.location.href=`sms:${n}` }}><MessageCircle size={16}/> SMS</button>}
              <select value={c.status} onChange={e=>setStatus2(c.id,e.target.value)} style={{padding:'8px',border:'1px solid var(--border)',borderRadius:10,background:'var(--surface2)',color:'var(--text)',fontSize:12}}>
                {STATUSES.map(s=><option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
              </select>
            </div>
          </div>
        ))}
      </div>

      <button className="fab" onClick={()=>setShowManual(true)} title="Add">+</button>

      {dragOver && <div className="toast">Drop file to import</div>}
      {status && <div className="toast">{status}</div>}
      {processing && <div className="toast">Processing… please wait</div>}

      {showImport && (
        <div className="modal-backdrop" onClick={()=>setShowImport(false)}>
          <div className="modal" onClick={e=>e.stopPropagation()}>
            <h2>Import Contacts</h2>
            <p style={{color:'var(--muted)',marginTop:0}}>Choose a source (or drag & drop a file)</p>
            <div style={{display:'grid',gap:8}}>
              {[
                {label:'Camera (scan paper list)',sub:'Take a photo and OCR it',color:'#7c3aed',icon:<Camera size={20}/>,ref:cameraInputRef,accept:'image/*',capture:'environment'},
                {label:'Image file',sub:'PNG, JPG from gallery',color:'#2563eb',icon:<ImageIcon size={20}/>,ref:fileInputRef,accept:'image/*'},
                {label:'PDF document',sub:'.pdf',color:'#dc2626',icon:<FileText size={20}/>,ref:genericInputRef,accept:'.pdf,application/pdf'},
                {label:'Excel / CSV',sub:'.xlsx, .xls, .csv',color:'#16a34a',icon:<FileSpreadsheet size={20}/>,ref:genericInputRef,accept:'.xlsx,.xls,.csv'},
                {label:'Word document',sub:'.docx',color:'#0d9488',icon:<FileType size={20}/>,ref:genericInputRef,accept:'.docx'},
                {label:'Any other file',sub:'.txt and more',color:'#64748b',icon:<Upload size={20}/>,ref:genericInputRef,accept:'*/*'},
              ].map((o,i)=>(
                <div key={i} className="importbtn" style={{flexDirection:'row',justifyContent:'flex-start',padding:12}} onClick={()=>{setShowImport(false);triggerInput(o.ref)}}>
                  <div className="ic" style={{background:o.color,width:40,height:40}}>{o.icon}</div>
                  <div style={{textAlign:'left'}}><div style={{fontWeight:600}}>{o.label}</div><div style={{fontSize:12,color:'var(--muted)'}}>{o.sub}</div></div>
                </div>
              ))}
            </div>
            <button className="btn btn-gray" style={{width:'100%',justifyContent:'center',marginTop:12}} onClick={()=>setShowImport(false)}>Cancel</button>
            <input ref={cameraInputRef} type="file" accept="image/*" capture="environment" style={{display:'none'}} onChange={e=>handleFile(e.target.files[0])} />
            <input ref={fileInputRef} type="file" accept="image/*" style={{display:'none'}} onChange={e=>handleFile(e.target.files[0])} />
            <input ref={genericInputRef} type="file" accept="*/*" style={{display:'none'}} onChange={e=>handleFile(e.target.files[0])} />
          </div>
        </div>
      )}

      {showManual && (
        <div className="modal-backdrop" onClick={()=>setShowManual(false)}>
          <div className="modal" onClick={e=>e.stopPropagation()}>
            <h2>Add Contact</h2>
            <input placeholder="Name" value={manualName} onChange={e=>setManualName(e.target.value)} />
            <input placeholder="Phone number" value={manualPhone} onChange={e=>setManualPhone(e.target.value)} />
            <textarea placeholder="Notes (optional)" value={manualNotes} onChange={e=>setManualNotes(e.target.value)} rows={2} />
            <div className="row">
              <button className="btn btn-primary" style={{flex:1,justifyContent:'center'}} onClick={()=>{
                if(manualPhone){ addContacts([{id:crypto.randomUUID(),name:manualName||'Unknown',phone:manualPhone,status:settings.defaultStatus||'new',notes:manualNotes,favorite:false,callCount:0,createdAt:Date.now(),calledAt:null}]); setManualName('');setManualPhone('');setManualNotes('');setShowManual(false) }
              }}>Save</button>
              <button className="btn btn-gray" onClick={()=>setShowManual(false)}>Cancel</button>
            </div>
          </div>
        </div>
      )}

      {showEdit && (
        <div className="modal-backdrop" onClick={()=>setShowEdit(null)}>
          <div className="modal" onClick={e=>e.stopPropagation()}>
            <h2>Edit Contact</h2>
            <input value={showEdit.name} onChange={e=>setShowEdit({...showEdit,name:e.target.value})} placeholder="Name" />
            <input value={showEdit.phone} onChange={e=>setShowEdit({...showEdit,phone:e.target.value})} placeholder="Phone" />
            <textarea value={showEdit.notes||''} onChange={e=>setShowEdit({...showEdit,notes:e.target.value})} placeholder="Notes" rows={3} />
            <div className="row">
              <button className="btn btn-primary" style={{flex:1,justifyContent:'center'}} onClick={()=>updateContact(showEdit)}>Save</button>
              <button className="btn btn-gray" onClick={()=>setShowEdit(null)}>Cancel</button>
            </div>
          </div>
        </div>
      )}

      {showSettings && (
        <div className="modal-backdrop" onClick={()=>setShowSettings(false)}>
          <div className="modal" onClick={e=>e.stopPropagation()}>
            <h2>Settings</h2>
            <label style={{fontSize:13,color:'var(--muted)'}}>Default country code</label>
            <input value={settings.countryCode} onChange={e=>setSettings({...settings,countryCode:e.target.value})} />
            <label style={{fontSize:13,color:'var(--muted)'}}>WhatsApp template (use {'{name}'})</label>
            <textarea value={settings.waTemplate} onChange={e=>setSettings({...settings,waTemplate:e.target.value})} rows={3} />
            <label style={{fontSize:13,color:'var(--muted)'}}>Accent color (Material You)</label>
            <div style={{display:'flex',gap:8,marginBottom:12,flexWrap:'wrap'}}>
              {['#2563eb','#16a34a','#7c3aed','#dc2626','#0d9488','#d97706','#db2777','#0891b2'].map(c=>(
                <div key={c} onClick={()=>setAccent(c)} style={{width:32,height:32,borderRadius:16,background:c,cursor:'pointer',border:accent===c?'3px solid var(--text)':'2px solid transparent'}} />
              ))}
            </div>
            <label style={{fontSize:13,color:'var(--muted)'}}>Default status for new imports</label>
            <select value={settings.defaultStatus} onChange={e=>setSettings({...settings,defaultStatus:e.target.value})} style={{width:'100%',padding:10,border:'1px solid var(--border)',borderRadius:10,background:'var(--surface2)',color:'var(--text)',marginBottom:10}}>
              {STATUSES.map(s=><option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
            <h3 style={{margin:'12px 0 6px',fontSize:14}}>Card appearance</h3>
            <label style={{fontSize:13,color:'var(--muted)'}}>Card style</label>
            <select value={settings.cardStyle} onChange={e=>setSettings({...settings,cardStyle:e.target.value})} style={{width:'100%',padding:10,border:'1px solid var(--border)',borderRadius:10,background:'var(--surface2)',color:'var(--text)',marginBottom:10}}>
              <option value="comfortable">Comfortable</option>
              <option value="compact">Compact</option>
            </select>
            {[
              ['tapToCall','Tap anywhere on card to call'],
              ['hideWhatsApp','Hide WhatsApp button'],
              ['showSms','Show SMS button'],
              ['showNotes','Show notes'],
              ['showStatus','Show status chip'],
              ['showCallCount','Show call count'],
              ['showLastCalled','Show last called date'],
            ].map(([k,label])=>(
              <label key={k} style={{display:'flex',alignItems:'center',gap:8,marginBottom:8,fontSize:14}}>
                <input type="checkbox" checked={!!settings[k]} onChange={e=>setSettings({...settings,[k]:e.target.checked})} />
                {label}
              </label>
            ))}
            <div className="row">
              <button className="btn btn-danger" onClick={clearAll}>Clear all contacts</button>
              <button className="btn btn-gray" onClick={()=>setShowSettings(false)}>Close</button>
            </div>
          </div>
        </div>
      )}

      {showStats && (
        <div className="modal-backdrop" onClick={()=>setShowStats(false)}>
          <div className="modal" onClick={e=>e.stopPropagation()}>
            <h2>Statistics</h2>
            {Object.entries(STATUS_LABEL).map(([k,v])=>(
              <div key={k} style={{display:'flex',justifyContent:'space-between',padding:'8px 0',borderBottom:'1px solid var(--border)'}}>
                <span>{v}</span><strong>{contacts.filter(c=>c.status===k).length}</strong>
              </div>
            ))}
            <div style={{display:'flex',justifyContent:'space-between',padding:'8px 0'}}><span>Total calls made</span><strong>{contacts.reduce((a,c)=>a+(c.callCount||0),0)}</strong></div>
            <div style={{display:'flex',justifyContent:'space-between',padding:'8px 0'}}><span>Favorites</span><strong>{contacts.filter(c=>c.favorite).length}</strong></div>
            <button className="btn btn-gray" style={{width:'100%',justifyContent:'center',marginTop:12}} onClick={()=>setShowStats(false)}>Close</button>
          </div>
        </div>
      )}
    </div>
  )
}
