const api = "http://localhost:8888/graph/";

$(function() {
    $("#input-file-button").click(function(){
        $("#input-file").click();
    });

    $("[data-back]").click(function(){
        const backElem = $(this).data("back");

        $(".screen").hide();
        $("#" + backElem).fadeIn(300);
    });

    $("#search-button").click(function(){
        $(".screen").hide();
        $("#search").fadeIn(300);
    });

    $("#do-search").click(function(){
        const query = $("#search-area input[name='query']").val();

        if( !query.length ) return;

        $(".lds-spinner").css("visibility", "visible");

        $.get(api + "papers/get?title=" + encodeURIComponent(query), function(data){
            $(".lds-spinner").css("visibility", "hidden");

            $("#search-results").html('<div class="result" data-title="' + data["title"] + '" data-topic="' + data['research_field'] + '">' +
                '<h2>' + data["title"] + '</h2>' +
                '<p>' + (data['discr'] || '') + '</p>' +
                '<span><b>Журнал: </b>' + data['journal_name'] + '</span>' +
                '<span><b>Тема: </b>' + data['research_field'] + '</span>' +
                '<span><b>Год: </b>' + data['year'] + '</span>' +
                '<a href="' + data['link'] + '" target="_blank">Читать</a>' +
                '</div>'
            );

            
            $(".result").click(function(){
                $(".screen").hide();
                $("#graph").fadeIn(300);

                renderTopicGraph($(this).data("title"), $(this).data("topic"));

                $.get(api + "research_fields/list", function(list){
                    $("#options select[name='theme'] option:not([disabled])").remove();

                    list.forEach(l => {
                        $("#options select[name='theme']").append('<option value="' + l + '">' + l + '</option>');
                    });
                });
            });
        });
    });

    $("#get-loops").click(function(){
        $(".screen").hide();
        $("#loops").fadeIn(300);
        $(".lds-spinner").css("visibility", "visible");

        $.get(api + "find_reference_cycles", function(list){

            $("#loops .loops").empty();
            $(".lds-spinner").css("visibility", "hidden");

            list.papers.forEach((paper, i) => {
                $("#loops .loops").append('<div class="loop" data-num="' + i + '"></div>');

                paper.forEach((elem, j) => {
                    $(".loop[data-num='" + i + "']").append('<div class="paper"><h2>' + elem['paper_title'] + '</h2><span>' + elem['author_name'] + '</span></div>');

                    if(paper.length !== j+1){
                        $(".loop[data-num='" + i + "']").append('<img class="arrow" src="img/arrow.png" />');
                    }
                });
            });
        });
    });
});

function renderTopicGraph(paper, topic) {
    
    $("#graph-area").empty();

    const nodesData = [
        //Статьи
        { id: 1, label: paper, shape: 'dot', color: 'rgb(59, 75, 252)', size: 20, type: 'paper' },
        
        //Темы
        { id: 2, label: topic, shape: 'dot', color: 'rgb(205, 162, 190)', size: 50, type: 'topic' },
    ];

    const edgesData = [
        { from: 1, to: 2, color: 'red', arrows:'to' },
        { from: 2, to: 1, color: 'rgb(55, 255, 65)', arrows:'to', dashes: true }
    ];

    const nodes = new vis.DataSet(nodesData);
    const edges = new vis.DataSet(edgesData);

    const container = document.getElementById('graph-area');
    const data = {
        nodes: nodes,
        edges: edges
    };
    const options = {
        physics: false,
        interaction: {
            dragNodes: true,// do not allow dragging nodes
            zoomView: false, // do not allow zooming
            dragView: false  // do not allow dragging
        }
    };
    const network = new vis.Network(container, data, options);

    network.on('click', function(properties) {
        const ids = properties.nodes;
        const clickedNodes = nodes.get(ids);

        if (clickedNodes.length) {
            const currPapers = nodesData.filter(n => {
                return clickedNodes.find(s => (s.id == n.id && n.type === 'paper')) !== undefined;
            });

            if(currPapers.length){
                $.get(api + "references/get?paper_title=" + currPapers[0].label, renderPapersGraph);
            }
        }
    });
}

function renderPapersGraph(list) {

    $("#graph-area").empty();

    const nodesData = [];
    const edgesData = [];

    list.forEach(d => {
        const from = d['source_paper_title'];
        const to = d['target_paper_title'];

        let fromIndex = nodesData.findIndex(n => n.label === from);

        if (fromIndex == -1){
            nodesData.push({ id: nodesData.length, label: from, shape: 'dot', size: 30, color: '#97C2FC' });
            fromIndex = nodesData.length - 1;
        }

        let toIndex = nodesData.findIndex(n => n.label === to);

        if (toIndex == -1){
            nodesData.push({ id: nodesData.length, label: to, shape: 'dot', size: 30, color: '#97C2FC' });
            toIndex = nodesData.length - 1;
        }

        if (edgesData.findIndex(e => e.from == fromIndex && e.to == toIndex) == -1 ){
            edgesData.push({ from: fromIndex, to: toIndex, color:{color:'red'}, arrows:'to' }, { from: toIndex, to: fromIndex, color:{color:'green'}, arrows:'to' });
        }
    });

    console.table(edgesData);

    const nodes = new vis.DataSet(nodesData);
    const edges = new vis.DataSet(edgesData);

    const container = document.getElementById('graph-area');
    const data = {
        nodes: nodes,
        edges: edges
    };
    const options = {
        physics: false,
        interaction: {
            dragNodes: true,// do not allow dragging nodes
            zoomView: false, // do not allow zooming
            dragView: false  // do not allow dragging
        }
    };
    const network = new vis.Network(container, data, options);

}