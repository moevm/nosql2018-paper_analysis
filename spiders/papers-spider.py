import scrapy
import re
from ast import literal_eval as make_tuple

number_categories_to_visit = 1

f = open('import.txt', 'w')



class PapersSpider(scrapy.Spider):
    name = 'abc'
    start_urls = ['https://cyberleninka.ru/']
    json = ""

    def closed(self, spider):
        f.write(self.json[:len(self.json) - 2] + '\n]}')
        f.close()

    def parse(self, response):
        self.json += '{"import_papers": [\n'
        cats_f = open('cats.txt', 'r')
        cats_t = map(lambda raw_t: make_tuple(raw_t.strip()), cats_f.readlines()[:number_categories_to_visit])
        for cat, cat_link in cats_t:
            cat_f = open(cat + '.txt', 'r')
            paper_links = list(map(lambda x: x.strip(), cat_f.readlines()[1:]))
            print(cat)

            yield from self.parse_papers(paper_links, response)

    def parse_papers(self, paper_links, response):
        for paper_link in paper_links:
            url = response.urljoin(paper_link)
            yield scrapy.Request(url, callback=self.parse_paper)

    def parse_paper(self, response):
        title = response.css('div>h1>i::text').extract_first()
        authors = response.css('.author-list>li>span::text').extract()
        journal_name = response.css('.infoblock>.half>span>a::text').extract_first()
        year = response.css('.year>time::text').extract_first()
        research_field = response.css('.half-right>ul>li>a::text').extract_first()
        references = response.css('.ocr>p::text').extract()

        print('GOT PAPER WITH:\n\t' +
              f'TITLE: {title}\n\t' +
              f'AUTHORS: {authors}\n\t' +
              f'JOURNAL: {journal_name}\n\t' +
              f'YEAR: {year}\n\t' +
              f'RESEARCH_FIELD: {research_field}')
        # regex1 = re.compile('(?:Cписок|СПИСОК).*\d{1,2}\.\s(?:(?:.\.){1,6} [\w-]{1,20},? ?){1,3}\s([^\./]+)(?://|\.)\s')
        # regex2 = re.compile('(?:Список|СПИСОК).*\d{1,2}\.\s(?:[\w-]{1,20} (?:.\.){1,6},? ?){1,3}\s([^\./]+)(?://|\.)\s')
        #
        # pattern1_matched = set(regex1.findall(' '.join(references)))
        # pattern2_matched = set(regex2def spider_closed(self, spider):.findall(' '.join(references)))
        # print(pattern1_matched | pattern2_matched)
        a = ', '.join(map(lambda x: '{ "name": "%s" }' % x, authors))
        authors_json = f'"authors": [{a}]'
        title_json = '"title": "%s"' % title
        journal_name_json = '"journal_name": "%s"' % journal_name
        research_field_json = '"research_field": "%s"' % research_field
        year_json = '"year": %s' % year
        link_json = '"link": "%s"' % response.request.url
        references_json = '"references": []'
        self.json += \
            "{ " + ', '.join([authors_json,
                              title_json,
                              journal_name_json,
                              research_field_json,
                              year_json,
                              link_json,
                              references_json]) + " },\n"
