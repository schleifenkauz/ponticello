rg -l --glob '*.json' 'ponticello.model.score.ProcessObject' | xargs sd 'ponticello.model.score.ProcessObject' 'SoundProcess'

rg -l  --glob '*.json' 'ponticello.model.score.SynthObject' | xargs sd 'ponticello.model.score.SynthObject' 'SoundProcess'

rg -l  --glob '*.json' 'ponticello.model.score.TaskObject' | xargs sd 'ponticello.model.score.ProcessObject' 'Task'

rg -l  --glob '*.json' 'ponticello.model.score.MemoObject' | xargs sd 'ponticello.model.score.MemoObject' 'Memo'

rg -l  --glob '*.json' 'ponticello.model.score.ScoreObjectGroup' | xargs sd 'ponticello.model.score.ScoreObjectGroup' 'SubScore'

rg -l  --glob '*.json' 'ponticello.model.score.TempoGridObject' | xargs sd 'ponticello.model.score.TempoGridObject' 'TempoGrid'

